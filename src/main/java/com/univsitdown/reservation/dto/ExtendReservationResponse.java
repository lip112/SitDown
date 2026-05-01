package com.univsitdown.reservation.dto;

import com.univsitdown.reservation.domain.Reservation;

public record ExtendReservationResponse(
        String id,
        String endAt,
        int extendedCount
) {
    public static ExtendReservationResponse from(Reservation r) {
        return new ExtendReservationResponse(
                r.getId().toString(),
                r.getEndAt() + "Z",
                r.getExtendedCount()
        );
    }
}
