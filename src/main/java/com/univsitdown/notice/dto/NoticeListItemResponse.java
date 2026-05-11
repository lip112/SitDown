package com.univsitdown.notice.dto;

import com.univsitdown.notice.domain.Notice;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record NoticeListItemResponse(
        String id,
        String title,
        String category,
        String publishedAt,
        boolean isNew
) {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.ofHours(9));

    public static NoticeListItemResponse from(Notice notice) {
        boolean isNew = notice.getPublishedAt().isAfter(
                java.time.Instant.now().minusSeconds(86400 * 7));
        return new NoticeListItemResponse(
                notice.getId().toString(),
                notice.getTitle(),
                notice.getCategory().name(),
                FORMATTER.format(notice.getPublishedAt()),
                isNew
        );
    }
}
