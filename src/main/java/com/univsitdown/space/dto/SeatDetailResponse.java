package com.univsitdown.space.dto;

import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.SeatStatus;

import java.util.List;

public record SeatDetailResponse(
        String id,
        String label,
        int row,
        int column,
        String status,
        List<String> features,
        String spaceId,
        String spaceName
) {
    public static SeatDetailResponse of(Seat seat, SeatStatus status) {
        return new SeatDetailResponse(
                seat.getId().toString(),
                seat.getLabel(),
                seat.getRowNum(),
                seat.getColNum(),
                status.name(),
                seat.getFeatures(),
                seat.getSpace().getId().toString(),
                seat.getSpace().getName()
        );
    }
}
