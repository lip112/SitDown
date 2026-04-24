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

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping
    public ResponseEntity<PageResponse<SpaceListItemResponse>> getSpaces(
            @RequestParam(required = false) SpaceCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                spaceService.getSpaces(category, keyword, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpaceDetailResponse> getSpace(@PathVariable UUID id) {
        return ResponseEntity.ok(spaceService.getSpace(id));
    }
}
