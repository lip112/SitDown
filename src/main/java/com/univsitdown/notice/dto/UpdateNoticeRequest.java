package com.univsitdown.notice.dto;

import com.univsitdown.notice.domain.NoticeCategory;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateNoticeRequest(
        @Size(min = 1, max = 200) String title,
        @Size(min = 1) String content,
        NoticeCategory category,
        Instant publishedAt,
        Instant expiresAt
) {}
