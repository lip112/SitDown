package com.univsitdown.notice.dto;

import com.univsitdown.global.util.DateTimeUtils;
import com.univsitdown.notice.domain.Notice;

public record NoticeListItemResponse(
        String id,
        String title,
        String category,
        String publishedAt,
        boolean isNew
) {
    public static NoticeListItemResponse from(Notice notice) {
        boolean isNew = notice.getPublishedAt().isAfter(
                java.time.Instant.now().minusSeconds(86400 * 7));
        return new NoticeListItemResponse(
                notice.getId().toString(),
                notice.getTitle(),
                notice.getCategory().name(),
                DateTimeUtils.toKst(notice.getPublishedAt()),
                isNew
        );
    }
}
