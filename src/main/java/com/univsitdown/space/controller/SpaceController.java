package com.univsitdown.space.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.global.security.CurrentUser;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CongestionPredictionResponse;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.service.FavoriteService;
import com.univsitdown.space.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;
    private final FavoriteService favoriteService;

    // SPACE-01: 공간 목록 조회
    @GetMapping
    public ResponseEntity<PageResponse<SpaceListItemResponse>> getSpaces(
            @RequestParam(required = false) SpaceCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                spaceService.getSpaces(category, keyword, PageRequest.of(page, Math.min(size, 100))));
    }

    // SPACE-02: 공간 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<SpaceDetailResponse> getSpace(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal principal) {
        UUID userId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(spaceService.getSpace(id, userId));
    }

    // SPACE-03: 혼잡도 예측 조회
    @GetMapping("/{id}/congestion")
    public CongestionPredictionResponse getCongestion(
            @PathVariable UUID id,
            @RequestParam(required = false) String date) {
        LocalDate targetDate = date != null
                ? LocalDate.parse(date)
                : LocalDate.now(ZoneOffset.ofHours(9));
        return spaceService.getCongestionPrediction(id, targetDate);
    }

    // SPACE-04: 즐겨찾기 추가
    @PostMapping("/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavorite(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal principal) {
        favoriteService.addFavorite(principal.userId(), id);
    }

    // SPACE-05: 즐겨찾기 해제
    @DeleteMapping("/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal principal) {
        favoriteService.removeFavorite(principal.userId(), id);
    }
}
