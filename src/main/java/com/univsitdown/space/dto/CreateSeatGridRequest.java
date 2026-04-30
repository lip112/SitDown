package com.univsitdown.space.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateSeatGridRequest(
        @Min(1) @Max(20) int rows,
        @Min(1) @Max(20) int columns,
        String labelPrefix,
        boolean overwrite
) {}
