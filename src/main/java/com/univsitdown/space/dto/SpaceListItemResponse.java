package com.univsitdown.space.dto;

import com.univsitdown.space.domain.Space;

import java.util.List;

// SPACE-01 목록 조회 응답 DTO (Java Record — 불변, 생성자·getter 자동 생성).
// stub 필드: Phase 진행에 따라 실제 값으로 교체 예정 (각 필드 주석 참고).
public record SpaceListItemResponse(
        String id,
        String name,
        int floor,
        String category,        // SpaceCategory.name() 문자열 (예: "READING_ROOM")
        int totalSeats,         // stub: Phase 2-3에서 Seat count 쿼리로 교체
        int availableSeats,     // stub: Phase 2-3에서 예약 미존재 좌석 count로 교체
        String congestion,      // stub: Phase 5에서 Redis 혼잡도 집계값으로 교체
        String openTime,        // "HH:mm:ss" 형식
        String closeTime,       // "HH:mm:ss" 형식
        List<String> features,
        String thumbnailUrl     // nullable
) {
    // Entity → DTO 변환. null 방어는 id만 필요 (테스트에서 save 전 엔티티에 id 없음).
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
