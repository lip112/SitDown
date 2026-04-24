package com.univsitdown.space.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @InjectMocks
    private SpaceService spaceService;

    private Space sampleSpace() {
        return Space.create("제1열람실", 3, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4,
                List.of("콘센트", "조용함"), null);
    }

    @Test
    void getSpaces_필터없음_목록조회_성공() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));

        PageResponse<SpaceListItemResponse> response = spaceService.getSpaces(null, null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).name()).isEqualTo("제1열람실");
        assertThat(response.content().get(0).totalSeats()).isEqualTo(0);
        assertThat(response.content().get(0).congestion()).isEqualTo("LOW");
    }

    @Test
    void getSpaces_category_필터_repository에_전달됨() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(SpaceCategory.READING_ROOM, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));

        spaceService.getSpaces(SpaceCategory.READING_ROOM, null, pageable);

        then(spaceRepository).should().findByFilters(SpaceCategory.READING_ROOM, null, pageable);
    }

    @Test
    void getSpace_정상조회_성공() {
        UUID id = UUID.randomUUID();
        given(spaceRepository.findById(id)).willReturn(Optional.of(sampleSpace()));

        SpaceDetailResponse response = spaceService.getSpace(id);

        assertThat(response.name()).isEqualTo("제1열람실");
        assertThat(response.isFavorite()).isFalse();
        assertThat(response.rows()).isEqualTo(0);
    }

    @Test
    void getSpace_없는ID_SpaceNotFoundException() {
        UUID id = UUID.randomUUID();
        given(spaceRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> spaceService.getSpace(id))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void createSpace_정상생성_성공() {
        CreateSpaceRequest request = new CreateSpaceRequest(
                "제2열람실", 2, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4,
                List.of("와이파이"), null
        );
        Space saved = Space.create(request.name(), request.floor(), request.category(),
                request.openTime(), request.closeTime(), request.maxReservationHours(),
                request.features(), request.thumbnailUrl());
        given(spaceRepository.save(any())).willReturn(saved);

        SpaceDetailResponse response = spaceService.createSpace(request);

        assertThat(response.name()).isEqualTo("제2열람실");
        assertThat(response.category()).isEqualTo("READING_ROOM");
        then(spaceRepository).should().save(any(Space.class));
    }
}
