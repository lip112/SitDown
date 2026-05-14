package com.univsitdown.notice.dto;

import com.univsitdown.notice.domain.NoticeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateNoticeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        @NotNull NoticeCategory category,
        Instant publishedAt,
        Instant expiresAt
) {}
