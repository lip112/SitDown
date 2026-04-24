package com.univsitdown.space.controller;

import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// ADMIN-01 공간 생성 담당. 관리자 전용 엔드포인트.
// Phase 3 전: ADMIN 역할 검증 미구현 (SecurityConfig에서 /api/admin/** 를 permitAll 처리).
//             Phase 3에서 @PreAuthorize("hasRole('ADMIN')") 추가 예정.
@RestController
@RequestMapping("/api/admin/spaces")
@RequiredArgsConstructor
public class AdminSpaceController {

    private final SpaceService spaceService;

    // 201 Created: 생성된 공간의 상세 정보 반환 (SpaceDetailResponse).
    // @Valid: CreateSpaceRequest의 Bean Validation 규칙 자동 검증. 실패 시 COMMON-100 반환.
    @PostMapping
    public ResponseEntity<SpaceDetailResponse> createSpace(
            @Valid @RequestBody CreateSpaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(spaceService.createSpace(request));
    }
}
