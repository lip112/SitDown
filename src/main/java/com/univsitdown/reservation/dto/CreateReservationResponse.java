package com.univsitdown.reservation.dto;

import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;

import java.time.temporal.ChronoUnit;

public record CreateReservationResponse(
        String id,
        String seatId,
        String seatLabel,
        String spaceId,
        String spaceName,
        String startAt,
        String endAt,
        int durationHours,
        String status,
        String createdAt
) {
    public static CreateReservationResponse from(Reservation r) {
        long minutes = ChronoUnit.MINUTES.between(r.getStartAt(), r.getEndAt());
        return new CreateReservationResponse(
                r.getId() != null ? r.getId().toString() : null,
                r.getSeat().getId().toString(),
                r.getSeat().getLabel(),
                r.getSeat().getSpace().getId().toString(),
                r.getSeat().getSpace().getName(),
                r.getStartAt() + "Z",
                r.getEndAt() + "Z",
                (int) (minutes / 60),
                ReservationStatus.SCHEDULED.name(),
                r.getCreatedAt() + "Z"
        );
    }
}
