package com.univsitdown.notice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.univsitdown.notice.domain.NoticeCategory;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class UpdateNoticeRequest {

    @Size(min = 1, max = 200)
    private String title;

    @Size(min = 1)
    private String content;

    private NoticeCategory category;
    private Instant publishedAt;
    private Instant expiresAt;
    private boolean expiresAtProvided;

    public String title() {
        return title;
    }

    public String content() {
        return content;
    }

    public NoticeCategory category() {
        return category;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @JsonSetter
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter
    public void setContent(String content) {
        this.content = content;
    }

    @JsonSetter
    public void setCategory(NoticeCategory category) {
        this.category = category;
    }

    @JsonSetter
    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    @JsonSetter
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        this.expiresAtProvided = true;
    }

    @JsonIgnore
    public boolean isExpiresAtProvided() {
        return expiresAtProvided;
    }
}
