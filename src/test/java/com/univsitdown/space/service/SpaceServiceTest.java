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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    @Mock SpaceRepository spaceRepository;
    @Mock SeatRepository seatRepository;
    @Mock ReservationRepository reservationRepository;
    @InjectMocks SpaceService spaceService;

    private Space sampleSpace() {
        Space space = Space.create("제1열람실", 3, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4,
                List.of("콘센트", "조용함"), null);
        ReflectionTestUtils.setField(space, "id", UUID.randomUUID());
        return space;
    }

    @Test
    void getSpaces_좌석10개_점유3개_AVAILABLE7개_LOW() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(10L);
        given(reservationRepository.countOccupiedBySpaceId(any(), any())).willReturn(3L);

        PageResponse<SpaceListItemResponse> response = spaceService.getSpaces(null, null, pageable);

        assertThat(response.content()).hasSize(1);
        SpaceListItemResponse item = response.content().get(0);
        assertThat(item.totalSeats()).isEqualTo(10);
        assertThat(item.availableSeats()).isEqualTo(7);
        assertThat(item.congestion()).isEqualTo("LOW");   // 3/10 = 30% < 40%
    }

    @Test
    void getSpaces_점유율40퍼이상_NORMAL() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(10L);
        given(reservationRepository.countOccupiedBySpaceId(any(), any())).willReturn(6L);

        PageResponse<SpaceListItemResponse> response = spaceService.getSpaces(null, null, pageable);

        assertThat(response.content().get(0).congestion()).isEqualTo("NORMAL"); // 6/10 = 60%
    }

    @Test
    void getSpaces_점유율75퍼이상_HIGH() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(10L);
        given(reservationRepository.countOccupiedBySpaceId(any(), any())).willReturn(8L);

        PageResponse<SpaceListItemResponse> response = spaceService.getSpaces(null, null, pageable);

        assertThat(response.content().get(0).congestion()).isEqualTo("HIGH"); // 8/10 = 80%
    }

    @Test
    void getSpaces_좌석없음_congestion_LOW() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(0L);
        given(reservationRepository.countOccupiedBySpaceId(any(), any())).willReturn(0L);

        PageResponse<SpaceListItemResponse> response = spaceService.getSpaces(null, null, pageable);

        assertThat(response.content().get(0).congestion()).isEqualTo("LOW");
    }

    @Test
    void getSpace_정상조회_stats_포함() {
        UUID id = UUID.randomUUID();
        Space space = sampleSpace();
        ReflectionTestUtils.setField(space, "id", id);
        given(spaceRepository.findById(id)).willReturn(Optional.of(space));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(id)).willReturn(20L);
        given(reservationRepository.countOccupiedBySpaceId(eq(id), any())).willReturn(5L);

        SpaceDetailResponse response = spaceService.getSpace(id);

        assertThat(response.name()).isEqualTo("제1열람실");
        assertThat(response.totalSeats()).isEqualTo(20);
        assertThat(response.availableSeats()).isEqualTo(15);
        assertThat(response.congestion()).isEqualTo("LOW"); // 5/20 = 25%
    }

    @Test
    void getSpace_없는ID_SpaceNotFoundException() {
        UUID id = UUID.randomUUID();
        given(spaceRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> spaceService.getSpace(id))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void createSpace_정상생성_stub값_반환() {
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
        assertThat(response.totalSeats()).isEqualTo(0); // 신규 공간 — 좌석 없음
        then(spaceRepository).should().save(any(Space.class));
    }
}
