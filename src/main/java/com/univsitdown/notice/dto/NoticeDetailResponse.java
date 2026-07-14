package com.univsitdown.notice.dto;

import com.univsitdown.global.util.DateTimeUtils;
import com.univsitdown.notice.domain.Notice;

public record NoticeDetailResponse(
        String id,
        String title,
        String content,
        String category,
        String publishedAt,
        String expiresAt,
        boolean isNew
) {
    public static NoticeDetailResponse from(Notice notice) {
        boolean isNew = notice.getPublishedAt().isAfter(
                java.time.Instant.now().minusSeconds(86400 * 7));
        return new NoticeDetailResponse(
                notice.getId().toString(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCategory().name(),
                DateTimeUtils.toKst(notice.getPublishedAt()),
                notice.getExpiresAt() == null ? null : DateTimeUtils.toKst(notice.getExpiresAt()),
                isNew
        );
    }
}
