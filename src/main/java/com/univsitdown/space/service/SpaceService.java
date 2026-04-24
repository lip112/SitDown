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

    // 공간 목록 조회. category/keyword가 null이면 전체 조회.
    // readOnly = true: 조회 전용 트랜잭션 — flush 생략으로 성능 최적화, 실수로 save 호출 방지.
    @Transactional(readOnly = true)
    public PageResponse<SpaceListItemResponse> getSpaces(SpaceCategory category, String keyword, Pageable pageable) {
        // JPQL 표준 LIKE 문법 사용: 쿼리 내부가 아닌 호출부에서 % 와일드카드 적용
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
