package com.univsitdown.space.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;

    @Transactional(readOnly = true)
    public PageResponse<SpaceListItemResponse> getSpaces(SpaceCategory category, String keyword, Pageable pageable) {
        String keywordPattern = keyword != null ? "%" + keyword + "%" : null;
        Page<SpaceListItemResponse> page = spaceRepository
                .findByFilters(category, keywordPattern, pageable)
                .map(SpaceListItemResponse::from);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public SpaceDetailResponse getSpace(UUID id) {
        Space space = spaceRepository.findById(id)
                .orElseThrow(SpaceNotFoundException::new);
        return SpaceDetailResponse.from(space);
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
}
