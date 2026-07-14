package com.univsitdown.notice.repository;

import com.univsitdown.notice.domain.Notice;
import com.univsitdown.notice.domain.NoticeCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {

    @Query("""
            SELECT n FROM Notice n
            WHERE n.isActive = true
            AND (:category IS NULL OR n.category = :category)
            ORDER BY n.publishedAt DESC
            """)
    Page<Notice> findActiveByCategory(@Param("category") NoticeCategory category, Pageable pageable);

    @Query("""
            SELECT n FROM Notice n
            WHERE n.isActive = true
            AND n.publishedAt <= :now
            AND (n.expiresAt IS NULL OR n.expiresAt > :now)
            AND (:category IS NULL OR n.category = :category)
            ORDER BY n.publishedAt DESC
            """)
    Page<Notice> findVisibleByCategory(@Param("category") NoticeCategory category,
                                       @Param("now") Instant now,
                                       Pageable pageable);
}
