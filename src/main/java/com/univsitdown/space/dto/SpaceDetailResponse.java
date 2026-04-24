package com.univsitdown.space.dto;

import com.univsitdown.space.domain.Space;

import java.util.List;

// SPACE-02 상세 조회 및 ADMIN-01 생성 응답에 공통으로 사용되는 DTO.
// SpaceListItemResponse의 모든 필드 포함 + rows/columns/maxReservationHours/images/isFavorite 추가.
public record SpaceDetailResponse(
        String id,
        String name,
        int floor,
        String category,
        int totalSeats,         // stub: Phase 2-3에서 Seat count 쿼리로 교체
        int availableSeats,     // stub: Phase 2-3에서 예약 미존재 좌석 count로 교체
        int rows,               // stub: Phase 2-3에서 좌석 max row로 교체
        int columns,            // stub: Phase 2-3에서 좌석 max column으로 교체
        String congestion,      // stub: Phase 5에서 Redis 혼잡도 집계값으로 교체
        String openTime,
        String closeTime,
        int maxReservationHours,
        List<String> features,
        List<String> images,    // stub: S3 이미지 URL 목록 (구현 시점 미정)
        boolean isFavorite      // stub: Phase 3에서 JWT 사용자 즐겨찾기 여부로 교체
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
                List.of(),  // stub: S3 이미지
                false       // stub: Phase 3 즐겨찾기
        );
    }
}
