package com.univsitdown.reservation.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;
import com.univsitdown.reservation.dto.*;
import com.univsitdown.reservation.exception.*;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.exception.SeatUnavailableException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.user.domain.User;
import com.univsitdown.user.exception.UserNotFoundException;
import com.univsitdown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;

    /**
     * 예약 생성 — 이 프로젝트의 핵심 메서드.
     *
     * 동시성 이중 방어선:
     *   1차) findByIdForUpdate → PostgreSQL row-level 락 (SELECT FOR UPDATE)
     *   2차) DB EXCLUDE 제약 (seat_id + tsrange) — 락이 뚫려도 중복 INSERT 차단
     *
     * 검증 순서가 성능에 영향을 미친다. 락 획득 전에 시간 유효성을 먼저 체크해
     * 무효한 요청이 불필요하게 DB 락을 경합하지 않도록 한다.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "space:list",   allEntries = true),
            @CacheEvict(value = "space:detail", allEntries = true),
            @CacheEvict(value = "seat:layout",  allEntries = true)
    })
    public CreateReservationResponse reserve(UUID userId, CreateReservationRequest request) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        LocalDateTime startAt = request.startAt();
        LocalDateTime endAt = request.endAt();

        // 락 획득 전 빠른 시간 유효성 검증 — DB 부하 최소화
        if (!endAt.isAfter(startAt)) throw new ReservationInvalidTimeException();

        // SELECT FOR UPDATE: 같은 seatId에 대한 동시 트랜잭션을 직렬화
        Seat seat = seatRepository.findByIdForUpdate(request.seatId())
                .orElseThrow(SeatNotFoundException::new);

        if (!seat.isEnabled()) throw new SeatUnavailableException();

        var space = seat.getSpace();
        if (!isWithinOperatingHours(startAt, endAt, space.getOpenTime(), space.getCloseTime())) {
            throw new ReservationOutOfHoursException();
        }

        long durationHours = ChronoUnit.HOURS.between(startAt, endAt);
        if (durationHours > space.getMaxReservationHours()) {
            throw new ReservationMaxDurationExceededException();
        }

        // BR-01: 사용자당 활성 예약 1건 제한
        if (reservationRepository.countActiveByUserId(userId, now) >= 1) {
            throw new UserReservationLimitException();
        }

        // 비관적 락 아래서 겹침 재확인 — 락 획득 사이에 다른 트랜잭션이 먼저 완료됐을 경우 대비
        if (reservationRepository.existsOverlapping(seat.getId(), startAt, endAt)) {
            throw new SeatAlreadyReservedException();
        }

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Reservation saved = reservationRepository.save(Reservation.create(user, seat, startAt, endAt));
        return CreateReservationResponse.from(saved);
    }

    private boolean isWithinOperatingHours(
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalTime openTime,
            LocalTime closeTime
    ) {
        boolean closesAtMidnight = closeTime.equals(LocalTime.MIDNIGHT);
        boolean endsAtNextMidnight = endAt.toLocalDate().equals(startAt.toLocalDate().plusDays(1))
                && endAt.toLocalTime().equals(LocalTime.MIDNIGHT);

        if (closesAtMidnight && endsAtNextMidnight) {
            return !startAt.toLocalTime().isBefore(openTime);
        }

        if (!startAt.toLocalDate().equals(endAt.toLocalDate())) {
            return false;
        }

        return !startAt.toLocalTime().isBefore(openTime)
                && (closesAtMidnight || !endAt.toLocalTime().isAfter(closeTime));
    }

    /**
     * IN_USE/COMPLETED 를 DB에 저장하지 않기 때문에,
     * PAST 탭 = "SCHEDULED이면서 endAt < now", ACTIVE 탭 = "SCHEDULED이면서 endAt >= now"로 필터링.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReservationListItemResponse> getMyReservations(
            UUID userId, String statusFilter, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        Page<Reservation> page = switch (statusFilter != null ? statusFilter : "ACTIVE") {
            case "PAST" -> reservationRepository.findPastByUserId(
                    userId, ReservationStatus.SCHEDULED, now, pageable);
            case "CANCELED" -> reservationRepository.findCanceledByUserId(
                    userId, List.of(ReservationStatus.CANCELED, ReservationStatus.NO_SHOW), pageable);
            default -> reservationRepository.findActiveByUserId(
                    userId, ReservationStatus.SCHEDULED, now, pageable);
        };
        return PageResponse.from(page.map(r -> ReservationListItemResponse.from(r, now)));
    }

    @Transactional(readOnly = true)
    public ReservationDetailResponse getReservation(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);
        if (!reservation.getUser().getId().equals(userId)) {
            throw new ReservationNotOwnerException();
        }
        return ReservationDetailResponse.from(reservation, LocalDateTime.now(ZoneOffset.ofHours(9)));
    }

    /**
     * 연장 시 겹침 검증은 현재 예약의 endAt~newEndAt 구간만 확인한다.
     * 자신을 제외(excludeId)해야 현재 예약 자신과의 충돌을 false positive로 잡지 않는다.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "space:list",   allEntries = true),
            @CacheEvict(value = "space:detail", allEntries = true),
            @CacheEvict(value = "seat:layout",  allEntries = true)
    })
    public ExtendReservationResponse extend(UUID reservationId, UUID userId, int additionalMinutes) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ReservationNotOwnerException();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        if (reservation.computedStatus(now) != ReservationStatus.IN_USE) {
            throw new ReservationNotExtendableException();
        }

        if (reservation.getExtendedCount() >= 2) {
            throw new ReservationMaxExtendExceededException();
        }

        LocalDateTime newEndAt = reservation.getEndAt().plusMinutes(additionalMinutes);

        if (reservationRepository.existsOverlappingExclude(
                reservation.getSeat().getId(), reservationId,
                reservation.getEndAt(), newEndAt)) {
            throw new ReservationExtendConflictException();
        }

        reservation.extend(newEndAt);
        return ExtendReservationResponse.from(reservation);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "space:list",   allEntries = true),
            @CacheEvict(value = "space:detail", allEntries = true),
            @CacheEvict(value = "seat:layout",  allEntries = true)
    })
    public void cancel(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ReservationNotOwnerException();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        if (reservation.computedStatus(now) == ReservationStatus.COMPLETED) {
            throw new ReservationAlreadyEndedException();
        }

        reservation.cancel(now);
    }
}
