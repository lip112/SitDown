package com.univsitdown.space.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.UserFavorite;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.space.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteRepository userFavoriteRepository;
    private final SpaceRepository spaceRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

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

    @Transactional(readOnly = true)
    public PageResponse<SpaceListItemResponse> getMyFavorites(UUID userId, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        Page<SpaceListItemResponse> page = userFavoriteRepository.findFavoriteSpacesByUserId(userId, pageable)
                .map(space -> toListItem(space, now));
        return PageResponse.from(page);
    }

    private SpaceListItemResponse toListItem(Space space, LocalDateTime now) {
        int total = (int) seatRepository.countBySpaceIdAndIsEnabledTrue(space.getId());
        int occupied = (int) reservationRepository.countOccupiedBySpaceId(space.getId(), now);
        return SpaceListItemResponse.from(space, total, total - occupied);
    }
}
