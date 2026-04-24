package com.univsitdown.space.dto;

import com.univsitdown.space.domain.SpaceCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record CreateSpaceRequest(
        @NotBlank String name,
        @Min(1) int floor,
        @NotNull SpaceCategory category,
        @NotNull LocalTime openTime,
        @NotNull LocalTime closeTime,
        @Min(1) @Max(8) int maxReservationHours,
        List<String> features,
        String thumbnailUrl
) {}
