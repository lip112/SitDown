package com.univsitdown.notice.dto;

import com.univsitdown.global.util.DateTimeUtils;
import com.univsitdown.notice.domain.Notice;

public record NoticeDetailResponse(
        String id,
        String title,
        String content,
        String category,
        String publishedAt
) {
    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(
                notice.getId().toString(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCategory().name(),
                DateTimeUtils.toKst(notice.getPublishedAt())
        );
    }
}
