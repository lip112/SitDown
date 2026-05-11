package com.univsitdown.reservation.dto;

import com.univsitdown.global.util.DateTimeUtils;
import com.univsitdown.reservation.domain.Reservation;

public record ExtendReservationResponse(
        String id,
        String endAt,
        int extendedCount
) {
    public static ExtendReservationResponse from(Reservation r) {
        return new ExtendReservationResponse(
                r.getId().toString(),
                DateTimeUtils.toKst(r.getEndAt()),
                r.getExtendedCount()
        );
    }
}
