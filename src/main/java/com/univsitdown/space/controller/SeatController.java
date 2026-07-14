package com.univsitdown.space.controller;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
import com.univsitdown.space.dto.SeatDetailResponse;
import com.univsitdown.space.dto.SeatLayoutResponse;
import com.univsitdown.space.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final SeatService seatService;

    @GetMapping("/api/spaces/{id}/seats")
    public SeatLayoutResponse getSeatLayout(
            @PathVariable UUID id,
            @RequestParam(required = false) String at) {
        return seatService.getSeatLayout(id, resolveAt(at));
    }

    @GetMapping("/api/seats/{id}")
    public SeatDetailResponse getSeatDetail(
            @PathVariable UUID id,
            @RequestParam(required = false) String at) {
        return seatService.getSeatDetail(id, resolveAt(at));
    }

    private LocalDateTime resolveAt(String at) {
        if (at == null) {
            return LocalDateTime.now(KST);
        }

        try {
            return OffsetDateTime.parse(at).withOffsetSameInstant(KST).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(at);
            } catch (DateTimeParseException ignored) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }
}
