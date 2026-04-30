package com.univsitdown.space.controller;

import com.univsitdown.space.dto.SeatDetailResponse;
import com.univsitdown.space.dto.SeatLayoutResponse;
import com.univsitdown.space.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/api/spaces/{id}/seats")
    public SeatLayoutResponse getSeatLayout(
            @PathVariable UUID id,
            @RequestParam(required = false) String at) {
        LocalDateTime atTime = at != null
                ? OffsetDateTime.parse(at).toLocalDateTime()
                : LocalDateTime.now(ZoneOffset.UTC);
        return seatService.getSeatLayout(id, atTime);
    }

    @GetMapping("/api/seats/{id}")
    public SeatDetailResponse getSeatDetail(
            @PathVariable UUID id,
            @RequestParam(required = false) String at) {
        LocalDateTime atTime = at != null
                ? OffsetDateTime.parse(at).toLocalDateTime()
                : LocalDateTime.now(ZoneOffset.UTC);
        return seatService.getSeatDetail(id, atTime);
    }
}
