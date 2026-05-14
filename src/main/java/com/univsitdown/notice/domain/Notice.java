package com.univsitdown.notice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeCategory category;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private Instant publishedAt;

    @Column
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Notice create(String title, String content, NoticeCategory category,
                                Instant publishedAt, Instant expiresAt) {
        Notice notice = new Notice();
        notice.title = title;
        notice.content = content;
        notice.category = category;
        notice.isActive = true;
        notice.publishedAt = publishedAt == null ? Instant.now() : publishedAt;
        notice.expiresAt = expiresAt;
        notice.createdAt = Instant.now();
        return notice;
    }

    public void update(String title, String content, NoticeCategory category,
                       Instant publishedAt, Instant expiresAt) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (category != null) {
            this.category = category;
        }
        if (publishedAt != null) {
            this.publishedAt = publishedAt;
        }
        if (expiresAt != null) {
            this.expiresAt = expiresAt;
        }
    }

    public void deactivate() {
        this.isActive = false;
    }
}
