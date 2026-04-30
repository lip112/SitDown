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
        return new SpaceListItemResponse(
                space.getId() != null ? space.getId().toString() : null,
                space.getName(),
                space.getFloor(),
                space.getCategory().name(),
                totalSeats,
                availableSeats,
                computeCongestion(totalSeats, availableSeats),
                space.getOpenTime().toString(),
                space.getCloseTime().toString(),
                space.getFeatures(),
                space.getThumbnailUrl()
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
