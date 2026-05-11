package com.univsitdown.space.service;

import com.univsitdown.space.domain.UserFavorite;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.space.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteRepository userFavoriteRepository;
    private final SpaceRepository spaceRepository;

    @Transactional
    public void addFavorite(UUID userId, UUID spaceId) {
        if (!spaceRepository.existsById(spaceId)) {
            throw new SpaceNotFoundException();
        }
        if (userFavoriteRepository.existsByUserIdAndSpaceId(userId, spaceId)) {
            return; // 이미 즐겨찾기 — 멱등 처리
        }
        userFavoriteRepository.save(UserFavorite.of(userId, spaceId));
    }

    @Transactional
    public void removeFavorite(UUID userId, UUID spaceId) {
        userFavoriteRepository.findByUserIdAndSpaceId(userId, spaceId)
                .ifPresent(userFavoriteRepository::delete);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(UUID userId, UUID spaceId) {
        return userFavoriteRepository.existsByUserIdAndSpaceId(userId, spaceId);
    }
}
