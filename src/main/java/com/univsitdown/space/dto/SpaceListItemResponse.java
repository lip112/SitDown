package com.univsitdown.space.dto;

import com.univsitdown.space.domain.Space;

import java.util.List;

public record SpaceListItemResponse(
        String id,
        String name,
        int floor,
        String category,
        int totalSeats,
        int availableSeats,
        String congestion,
        String openTime,
        String closeTime,
        List<String> features,
        String thumbnailUrl
) {
    public static SpaceListItemResponse from(Space space) {
        return from(space, 0, 0);
    }

    public static SpaceListItemResponse from(Space space, int totalSeats, int availableSeats) {
        return from(space, totalSeats, totalSeats, availableSeats);
    }

    public static SpaceListItemResponse from(Space space, int totalSeats,
                                             int enabledSeats, int availableSeats) {
        return new SpaceListItemResponse(
                space.getId() != null ? space.getId().toString() : null,
                space.getName(),
                space.getFloor(),
                space.getCategory().name(),
                totalSeats,
                availableSeats,
                computeCongestion(enabledSeats, availableSeats),
                space.getOpenTime().toString(),
                space.getCloseTime().toString(),
                space.getFeatures(),
                space.getThumbnailUrl()
        );
    }

    private static String computeCongestion(int enabledSeats, int availableSeats) {
        if (enabledSeats == 0) return "LOW";
        double occupancyRate = (double) (enabledSeats - availableSeats) / enabledSeats;
        if (occupancyRate < 0.40) return "LOW";
        if (occupancyRate < 0.75) return "NORMAL";
        return "HIGH";
    }
}
