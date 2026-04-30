package com.univsitdown.space.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "space:list",
               key = "#category + ':' + #keyword + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PageResponse<SpaceListItemResponse> getSpaces(SpaceCategory category, String keyword, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String keywordPattern = keyword != null ? "%" + keyword + "%" : null;
        Page<SpaceListItemResponse> page = spaceRepository
                .findByFilters(category, keywordPattern, pageable)
                .map(space -> toListItem(space, now));
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "space:detail", key = "#id")
    public SpaceDetailResponse getSpace(UUID id) {
        Space space = spaceRepository.findById(id)
                .orElseThrow(SpaceNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int total = (int) seatRepository.countBySpaceIdAndIsEnabledTrue(id);
        int occupied = (int) reservationRepository.countOccupiedBySpaceId(id, now);
        return SpaceDetailResponse.from(space, total, total - occupied);
    }

    @Transactional
    public SpaceDetailResponse createSpace(CreateSpaceRequest request) {
        Space space = Space.create(
                request.name(),
                request.floor(),
                request.category(),
                request.openTime(),
                request.closeTime(),
                request.maxReservationHours(),
                request.features(),
                request.thumbnailUrl()
        );
        return SpaceDetailResponse.from(spaceRepository.save(space));
    }

    private SpaceListItemResponse toListItem(Space space, LocalDateTime now) {
        int total = (int) seatRepository.countBySpaceIdAndIsEnabledTrue(space.getId());
        int occupied = (int) reservationRepository.countOccupiedBySpaceId(space.getId(), now);
        return SpaceListItemResponse.from(space, total, total - occupied);
    }
}
