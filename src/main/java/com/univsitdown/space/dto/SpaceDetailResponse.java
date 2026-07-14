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

    public static SpaceDetailResponse from(Space space, int totalSeats, int availableSeats, boolean isFavorite) {
        return from(space, totalSeats, availableSeats, 0, 0, isFavorite);
    }

    public static SpaceDetailResponse from(Space space, int totalSeats, int availableSeats,
                                           int rows, int columns, boolean isFavorite) {
        return from(space, totalSeats, totalSeats, availableSeats, rows, columns, isFavorite);
    }

    public static SpaceDetailResponse from(Space space, int totalSeats, int enabledSeats,
                                           int availableSeats, int rows, int columns, boolean isFavorite) {
        return new SpaceDetailResponse(
                space.getId() != null ? space.getId().toString() : null,
                space.getName(),
                space.getFloor(),
                space.getCategory().name(),
                totalSeats,
                availableSeats,
                rows,
                columns,
                computeCongestion(enabledSeats, availableSeats),
                space.getOpenTime().toString(),
                space.getCloseTime().toString(),
                space.getMaxReservationHours(),
                space.getFeatures(),
                imagesOf(space),
                isFavorite
        );
    }

    public static SpaceDetailResponse from(Space space, int totalSeats, int availableSeats) {
        return from(space, totalSeats, availableSeats, false);
    }

    private static List<String> imagesOf(Space space) {
        String thumbnailUrl = space.getThumbnailUrl();
        return thumbnailUrl == null || thumbnailUrl.isBlank() ? List.of() : List.of(thumbnailUrl);
    }

    private static String computeCongestion(int enabledSeats, int availableSeats) {
        if (enabledSeats == 0) return "LOW";
        double occupancyRate = (double) (enabledSeats - availableSeats) / enabledSeats;
        if (occupancyRate < 0.40) return "LOW";
        if (occupancyRate < 0.75) return "NORMAL";
        return "HIGH";
    }
}
