package com.univsitdown.space.dto;

import com.univsitdown.space.domain.Space;

import java.util.List;

public record SpaceDetailResponse(
        String id,
        String name,
        int floor,
        String category,
        int totalSeats,
        int availableSeats,
        int rows,
        int columns,
        String congestion,
        String openTime,
        String closeTime,
        int maxReservationHours,
        List<String> features,
        List<String> images,
        boolean isFavorite
) {
    public static SpaceDetailResponse from(Space space) {
        return from(space, 0, 0);
    }

    public static SpaceDetailResponse from(Space space, int totalSeats, int availableSeats) {
        return new SpaceDetailResponse(
                space.getId() != null ? space.getId().toString() : null,
                space.getName(),
                space.getFloor(),
                space.getCategory().name(),
                totalSeats,
                availableSeats,
                0,
                0,
                computeCongestion(totalSeats, availableSeats),
                space.getOpenTime().toString(),
                space.getCloseTime().toString(),
                space.getMaxReservationHours(),
                space.getFeatures(),
                List.of(),
                false
        );
    }

    private static String computeCongestion(int totalSeats, int availableSeats) {
        if (totalSeats == 0) return "LOW";
        double occupancyRate = (double) (totalSeats - availableSeats) / totalSeats;
        if (occupancyRate < 0.40) return "LOW";
        if (occupancyRate < 0.75) return "NORMAL";
        return "HIGH";
    }
}
