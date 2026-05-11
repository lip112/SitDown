package com.univsitdown.notice.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public PageResponse<NoticeListItemResponse> getNotices(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noticeService.getNotices(category, PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/{id}")
    public NoticeDetailResponse getNotice(@PathVariable UUID id) {
        return noticeService.getNotice(id);
    }
}
