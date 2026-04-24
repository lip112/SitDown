package com.univsitdown.space.dto;

import com.univsitdown.space.domain.SpaceCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

// ADMIN-01 공간 생성 요청 DTO. @Valid 적용 시 아래 Bean Validation 규칙이 자동 검증됨.
public record CreateSpaceRequest(
        @NotBlank @Size(max = 100) String name,  // DB 컬럼 VARCHAR(100)과 일치
        @Min(1) int floor,
        @NotNull SpaceCategory category,
        @NotNull LocalTime openTime,
        @NotNull LocalTime closeTime,
        // @Max(8): 공간별 정책 최대치. 비즈니스 기본값(BR-02)은 4시간이지만 공간 설정으로 최대 8시간까지 허용
        @Min(1) @Max(8) int maxReservationHours,
        List<String> features,   // optional — null이면 Space.create()에서 빈 리스트로 처리
        String thumbnailUrl      // optional
) {}
