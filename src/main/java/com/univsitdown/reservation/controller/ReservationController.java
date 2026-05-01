package com.univsitdown.reservation.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.global.security.CurrentUser;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.reservation.dto.*;
import com.univsitdown.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<CreateReservationResponse> reserve(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.reserve(principal.userId(), request));
    }

    @GetMapping("/me")
    public PageResponse<ReservationListItemResponse> getMyReservations(
            @CurrentUser UserPrincipal principal,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return reservationService.getMyReservations(principal.userId(), status, pageable);
    }

    @GetMapping("/{id}")
    public ReservationDetailResponse getReservation(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID id) {
        return reservationService.getReservation(id, principal.userId());
    }

    @PatchMapping("/{id}/extend")
    public ResponseEntity<ExtendReservationResponse> extend(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ExtendReservationRequest request) {
        return ResponseEntity.ok(reservationService.extend(id, principal.userId(), request.additionalMinutes()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID id) {
        reservationService.cancel(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
