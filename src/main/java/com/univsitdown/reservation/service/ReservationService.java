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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Transactional
    public CreateReservationResponse reserve(UUID userId, CreateReservationRequest request) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime startAt = request.startAt();
        LocalDateTime endAt = request.endAt();

        if (!endAt.isAfter(startAt)) throw new ReservationInvalidTimeException();

        // SELECT FOR UPDATE로 row-level 락 획득
        Seat seat = seatRepository.findByIdForUpdate(request.seatId())
                .orElseThrow(SeatNotFoundException::new);

        if (!seat.isEnabled()) throw new SeatUnavailableException();

        var space = seat.getSpace();
        if (startAt.toLocalTime().isBefore(space.getOpenTime()) ||
            endAt.toLocalTime().isAfter(space.getCloseTime())) {
            throw new ReservationOutOfHoursException();
        }

        long durationHours = ChronoUnit.HOURS.between(startAt, endAt);
        if (durationHours > space.getMaxReservationHours()) {
            throw new ReservationMaxDurationExceededException();
        }

        if (reservationRepository.countActiveByUserId(userId, now) >= 1) {
            throw new UserReservationLimitException();
        }

        if (reservationRepository.existsOverlapping(seat.getId(), startAt, endAt)) {
            throw new SeatAlreadyReservedException();
        }

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Reservation saved = reservationRepository.save(Reservation.create(user, seat, startAt, endAt));
        return CreateReservationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationListItemResponse> getMyReservations(
            UUID userId, String statusFilter, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
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
        return ReservationDetailResponse.from(reservation, LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public ExtendReservationResponse extend(UUID reservationId, UUID userId, int additionalMinutes) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ReservationNotOwnerException();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
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
    public void cancel(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ReservationNotOwnerException();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (reservation.computedStatus(now) == ReservationStatus.COMPLETED) {
            throw new ReservationAlreadyEndedException();
        }

        reservation.cancel(now);
    }
}
