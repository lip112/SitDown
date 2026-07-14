package com.univsitdown.notice.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.notice.dto.CreateNoticeRequest;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.dto.UpdateNoticeRequest;
import com.univsitdown.notice.service.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public PageResponse<NoticeListItemResponse> getNotices(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noticeService.getAdminNotices(category, PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/{id}")
    public NoticeDetailResponse getNotice(@PathVariable UUID id) {
        return noticeService.getAdminNotice(id);
    }

    @PostMapping
    public ResponseEntity<NoticeDetailResponse> createNotice(
            @Valid @RequestBody CreateNoticeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(noticeService.createNotice(request));
    }

    @PatchMapping("/{id}")
    public NoticeDetailResponse updateNotice(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNoticeRequest request) {
        return noticeService.updateNotice(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotice(@PathVariable UUID id) {
        noticeService.deleteNotice(id);
        return ResponseEntity.noContent().build();
    }
}
