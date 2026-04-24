package com.univsitdown.space.repository;

import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID> {

    @Query("""
            SELECT s FROM Space s
            WHERE (:category IS NULL OR s.category = :category)
              AND (:keyword IS NULL OR s.name LIKE %:keyword%)
            """)
    Page<Space> findByFilters(@Param("category") SpaceCategory category,
                              @Param("keyword") String keyword,
                              Pageable pageable);
}
