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

    // category, keyword 둘 다 null이면 전체 조회.
    // LIKE :keyword — 와일드카드(%)는 호출부(SpaceService)에서 붙여서 전달.
    //                 쿼리 내 %:keyword% 문법은 비표준이라 JPA 구현체에 따라 동작이 다를 수 있음.
    @Query("""
            SELECT s FROM Space s
            WHERE (:category IS NULL OR s.category = :category)
              AND (:keyword IS NULL OR s.name LIKE :keyword)
            """)
    Page<Space> findByFilters(@Param("category") SpaceCategory category,
                              @Param("keyword") String keyword,
                              Pageable pageable);
}
