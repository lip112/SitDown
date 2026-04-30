package com.univsitdown.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExtendReservationRequest(
        @NotNull @Min(1) @Max(120) Integer additionalMinutes
) {}
