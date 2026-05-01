package com.univsitdown.space.dto;

public record CreateSeatGridResponse(
        String spaceId,
        int createdCount,
        int rows,
        int columns
) {}
