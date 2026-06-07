package com.univsitdown.space.repository;

import com.univsitdown.space.domain.UserFavorite;
import com.univsitdown.space.domain.Space;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UUID> {

    boolean existsByUserIdAndSpaceId(UUID userId, UUID spaceId);

    Optional<UserFavorite> findByUserIdAndSpaceId(UUID userId, UUID spaceId);

    @Query(value = """
            SELECT s FROM Space s
            JOIN UserFavorite f ON f.spaceId = s.id
            WHERE f.userId = :userId
            ORDER BY f.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(s) FROM Space s
            JOIN UserFavorite f ON f.spaceId = s.id
            WHERE f.userId = :userId
            """)
    Page<Space> findFavoriteSpacesByUserId(@Param("userId") UUID userId, Pageable pageable);
}
