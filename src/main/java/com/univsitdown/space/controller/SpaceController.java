package com.univsitdown.space.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// SPACE-01 목록 조회, SPACE-02 상세 조회 담당. 비즈니스 로직 없이 Service에 위임만 함.
// Phase 3 전: 인증 미구현으로 SecurityConfig에서 /api/spaces/** 를 permitAll 처리.
@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    // SPACE-01: 공간 목록 조회. category/keyword 미전달 시 전체 조회.
    // size 상한 100: API 스펙 기준 (Math.min으로 클라이언트 초과 요청 방어).
    @GetMapping
    public ResponseEntity<PageResponse<SpaceListItemResponse>> getSpaces(
            @RequestParam(required = false) SpaceCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                spaceService.getSpaces(category, keyword, PageRequest.of(page, Math.min(size, 100))));
    }

    // SPACE-02: 공간 상세 조회. 없는 ID면 SpaceNotFoundException → 404 반환.
    @GetMapping("/{id}")
    public ResponseEntity<SpaceDetailResponse> getSpace(@PathVariable UUID id) {
        return ResponseEntity.ok(spaceService.getSpace(id));
    }
}
