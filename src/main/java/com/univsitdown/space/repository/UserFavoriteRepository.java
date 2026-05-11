package com.univsitdown.space.repository;

import com.univsitdown.space.domain.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UUID> {

    boolean existsByUserIdAndSpaceId(UUID userId, UUID spaceId);

    Optional<UserFavorite> findByUserIdAndSpaceId(UUID userId, UUID spaceId);
}
