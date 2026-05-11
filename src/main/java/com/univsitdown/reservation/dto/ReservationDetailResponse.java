package com.univsitdown.reservation.dto;

import com.univsitdown.global.util.DateTimeUtils;
import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record ReservationDetailResponse(
        String id,
        String seatId,
        String seatLabel,
        String spaceId,
        String spaceName,
        int spaceFloor,
        String startAt,
        String endAt,
        int durationHours,
        String status,
        Long remainingSeconds,
        int extendedCount,
        String createdAt
) {
    public static ReservationDetailResponse from(Reservation r, LocalDateTime now) {
        ReservationStatus computed = r.computedStatus(now);
        Long remaining = null;
        if (computed == ReservationStatus.IN_USE) {
            remaining = ChronoUnit.SECONDS.between(now, r.getEndAt());
        }
        long minutes = ChronoUnit.MINUTES.between(r.getStartAt(), r.getEndAt());
        return new ReservationDetailResponse(
                r.getId().toString(),
                r.getSeat().getId().toString(),
                r.getSeat().getLabel(),
                r.getSeat().getSpace().getId().toString(),
                r.getSeat().getSpace().getName(),
                r.getSeat().getSpace().getFloor(),
                DateTimeUtils.toKst(r.getStartAt()),
                DateTimeUtils.toKst(r.getEndAt()),
                (int) (minutes / 60),
                computed.name(),
                remaining,
                r.getExtendedCount(),
                DateTimeUtils.toKst(r.getCreatedAt())
        );
    }
}
