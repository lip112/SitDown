package com.univsitdown.notice.dto;

import com.univsitdown.notice.domain.Notice;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record NoticeDetailResponse(
        String id,
        String title,
        String content,
        String category,
        String publishedAt
) {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.ofHours(9));

    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(
                notice.getId().toString(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCategory().name(),
                FORMATTER.format(notice.getPublishedAt())
        );
    }
}
