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
        return new SpaceDetailResponse(
                space.getId() != null ? space.getId().toString() : null,
                space.getName(),
                space.getFloor(),
                space.getCategory().name(),
                0,          // stub: Phase 2-3
                0,          // stub: Phase 2-3
                0,          // stub: Phase 2-3
                0,          // stub: Phase 2-3
                "LOW",      // stub: Phase 5
                space.getOpenTime().toString(),
                space.getCloseTime().toString(),
                space.getMaxReservationHours(),
                space.getFeatures(),
                List.of(),  // stub: 미정 (S3 이미지)
                false       // stub: Phase 3 즐겨찾기
        );
    }
}
