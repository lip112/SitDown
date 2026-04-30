package com.univsitdown.space.dto;

import java.util.List;

public record SeatLayoutResponse(
        String spaceId,
        int rows,
        int columns,
        List<SeatItemResponse> seats
) {}
