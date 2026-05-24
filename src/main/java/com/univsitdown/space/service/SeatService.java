package com.univsitdown.space.service;

import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.SeatStatus;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.dto.*;
import com.univsitdown.space.exception.SeatAlreadyExistsException;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "space:list",   allEntries = true),
            @CacheEvict(value = "space:detail", allEntries = true),
            @CacheEvict(value = "seat:layout",  allEntries = true)
    })
    public CreateSeatGridResponse createGrid(UUID spaceId, CreateSeatGridRequest request) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(SpaceNotFoundException::new);

        if (seatRepository.existsBySpaceId(spaceId)) {
            if (!request.overwrite()) throw new SeatAlreadyExistsException();
            seatRepository.deleteBySpaceId(spaceId);
        }

        String prefix = (request.labelPrefix() != null && !request.labelPrefix().isBlank())
                ? request.labelPrefix() : "A";
        char baseChar = prefix.charAt(0);

        List<Seat> seats = new ArrayList<>();
        for (int r = 1; r <= request.rows(); r++) {
            String rowLetter = String.valueOf((char) (baseChar + r - 1));
            for (int c = 1; c <= request.columns(); c++) {
                seats.add(Seat.create(space, r, c, rowLetter + "-" + c));
            }
        }
        seatRepository.saveAll(seats);

        return new CreateSeatGridResponse(spaceId.toString(), seats.size(), request.rows(), request.columns());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "space:list",   allEntries = true),
            @CacheEvict(value = "space:detail", allEntries = true),
            @CacheEvict(value = "seat:layout",  allEntries = true)
    })
    public void updateSeatStatus(UUID seatId, boolean isEnabled) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(SeatNotFoundException::new);
        seat.updateEnabled(isEnabled);
    }

    @Transactional(readOnly = true)
    public SeatLayoutResponse getSeatLayout(UUID spaceId, LocalDateTime at) {
        spaceRepository.findById(spaceId).orElseThrow(SpaceNotFoundException::new);

        List<Seat> seats = seatRepository.findBySpaceIdOrderByRowNumAscColNumAsc(spaceId);
        Set<UUID> occupiedIds = Set.copyOf(reservationRepository.findOccupiedSeatIdsBySpaceId(spaceId, at));

        int maxRow = seats.stream().mapToInt(Seat::getRowNum).max().orElse(0);
        int maxCol = seats.stream().mapToInt(Seat::getColNum).max().orElse(0);

        List<SeatItemResponse> seatResponses = seats.stream()
                .map(seat -> SeatItemResponse.of(seat, resolveSeatStatus(seat, occupiedIds)))
                .collect(Collectors.toList());

        return new SeatLayoutResponse(spaceId.toString(), maxRow, maxCol, seatResponses);
    }

    @Transactional(readOnly = true)
    public SeatDetailResponse getSeatDetail(UUID seatId, LocalDateTime at) {
        Seat seat = seatRepository.findById(seatId).orElseThrow(SeatNotFoundException::new);
        Set<UUID> occupiedIds = Set.copyOf(
                reservationRepository.findOccupiedSeatIdsBySpaceId(seat.getSpace().getId(), at));
        return SeatDetailResponse.of(seat, resolveSeatStatus(seat, occupiedIds));
    }

    /**
     * 관리자 비활성화(isEnabled=false)가 현재 점유 여부보다 우선한다.
     * 점검 중인 좌석이 우연히 예약 없는 상태라도 AVAILABLE로 보이면 안 되기 때문이다.
     */
    private SeatStatus resolveSeatStatus(Seat seat, Set<UUID> occupiedIds) {
        if (!seat.isEnabled()) return SeatStatus.UNAVAILABLE;
        if (occupiedIds.contains(seat.getId())) return SeatStatus.OCCUPIED;
        return SeatStatus.AVAILABLE;
    }
}
