package com.univsitdown.space.service;

import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.dto.CreateSeatGridRequest;
import com.univsitdown.space.dto.CreateSeatGridResponse;
import com.univsitdown.space.exception.SeatAlreadyExistsException;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final SpaceRepository spaceRepository;

    @Transactional
    public CreateSeatGridResponse createGrid(UUID spaceId, CreateSeatGridRequest request) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(SpaceNotFoundException::new);

        if (seatRepository.existsBySpaceId(spaceId)) {
            if (!request.overwrite()) {
                throw new SeatAlreadyExistsException();
            }
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
    public void updateSeatStatus(UUID seatId, boolean isEnabled) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(SeatNotFoundException::new);
        seat.updateEnabled(isEnabled);
    }
}
