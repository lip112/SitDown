package com.univsitdown.space.controller;

import com.univsitdown.space.dto.CreateSeatGridRequest;
import com.univsitdown.space.dto.CreateSeatGridResponse;
import com.univsitdown.space.dto.UpdateSeatStatusRequest;
import com.univsitdown.space.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminSeatController {

    private final SeatService seatService;

    @PostMapping("/api/admin/spaces/{id}/seats/grid")
    public ResponseEntity<CreateSeatGridResponse> createGrid(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSeatGridRequest request) {
        return ResponseEntity.ok(seatService.createGrid(id, request));
    }

    @PatchMapping("/api/admin/seats/{id}")
    public ResponseEntity<Void> updateSeatStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSeatStatusRequest request) {
        seatService.updateSeatStatus(id, request.isEnabled());
        return ResponseEntity.ok().build();
    }
}
