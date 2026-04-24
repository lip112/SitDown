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
        return new SpaceListItemResponse(
                space.getId() != null ? space.getId().toString() : null,
                space.getName(),
                space.getFloor(),
                space.getCategory().name(),
                0,       // stub: Phase 2-3에서 Seat count 쿼리로 교체
                0,       // stub: Phase 2-3에서 예약 미존재 좌석 count로 교체
                "LOW",   // stub: Phase 5에서 Redis 집계값으로 교체
                space.getOpenTime().toString(),
                space.getCloseTime().toString(),
                space.getFeatures(),
                space.getThumbnailUrl()
        );
    }
}
