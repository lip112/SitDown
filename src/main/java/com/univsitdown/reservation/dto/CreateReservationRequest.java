package com.univsitdown.reservation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID seatId,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt
) {}
