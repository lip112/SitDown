package com.univsitdown.space.dto;

import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.SeatStatus;

import java.util.List;

public record SeatItemResponse(
        String id,
        String label,
        int row,
        int column,
        String status,
        List<String> features
) {
    public static SeatItemResponse of(Seat seat, SeatStatus status) {
        return new SeatItemResponse(
                seat.getId().toString(),
                seat.getLabel(),
                seat.getRowNum(),
                seat.getColNum(),
                status.name(),
                seat.getFeatures()
        );
    }
}
