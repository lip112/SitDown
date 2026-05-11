package com.univsitdown.reservation.dto;

import com.univsitdown.global.util.DateTimeUtils;
import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record ReservationListItemResponse(
        String id,
        String seatLabel,
        String spaceName,
        int spaceFloor,
        String startAt,
        String endAt,
        String status,
        Long remainingSeconds
) {
    public static ReservationListItemResponse from(Reservation r, LocalDateTime now) {
        ReservationStatus computed = r.computedStatus(now);
        Long remaining = null;
        if (computed == ReservationStatus.IN_USE) {
            remaining = ChronoUnit.SECONDS.between(now, r.getEndAt());
        }
        return new ReservationListItemResponse(
                r.getId().toString(),
                r.getSeat().getLabel(),
                r.getSeat().getSpace().getName(),
                r.getSeat().getSpace().getFloor(),
                DateTimeUtils.toKst(r.getStartAt()),
                DateTimeUtils.toKst(r.getEndAt()),
                computed.name(),
                remaining
        );
    }
}
