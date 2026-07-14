package com.univsitdown.space.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.UserFavorite;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.space.repository.UserFavoriteRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    UserFavoriteRepository userFavoriteRepository;

    @Mock
    SpaceRepository spaceRepository;

    @Mock
    SeatRepository seatRepository;

    @Mock
    ReservationRepository reservationRepository;

    @InjectMocks
    FavoriteService favoriteService;

    @Test
    void addFavorite_공간없으면_예외() {
        given(spaceRepository.existsById(any())).willReturn(false);
        assertThatThrownBy(() -> favoriteService.addFavorite(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void addFavorite_이미존재하면_멱등처리() {
        given(spaceRepository.existsById(any())).willReturn(true);
        given(userFavoriteRepository.existsByUserIdAndSpaceId(any(), any())).willReturn(true);
        favoriteService.addFavorite(UUID.randomUUID(), UUID.randomUUID());
        verify(userFavoriteRepository, never()).save(any());
    }

    @Test
    void addFavorite_정상저장() {
        given(spaceRepository.existsById(any())).willReturn(true);
        given(userFavoriteRepository.existsByUserIdAndSpaceId(any(), any())).willReturn(false);
        given(userFavoriteRepository.save(any())).willReturn(mock(UserFavorite.class));
        favoriteService.addFavorite(UUID.randomUUID(), UUID.randomUUID());
        verify(userFavoriteRepository).save(any());
    }

    @Test
    void removeFavorite_없으면_무시() {
        given(userFavoriteRepository.findByUserIdAndSpaceId(any(), any())).willReturn(Optional.empty());
        favoriteService.removeFavorite(UUID.randomUUID(), UUID.randomUUID());
        verify(userFavoriteRepository, never()).delete(any());
    }

    @Test
    void getMyFavorites_즐겨찾기공간을_좌석수와_함께_반환() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        Space space = Space.create("제1열람실", 3, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4,
                List.of("콘센트"), null);
        ReflectionTestUtils.setField(space, "id", spaceId);

        given(userFavoriteRepository.findFavoriteSpacesByUserId(userId, pageable))
                .willReturn(new PageImpl<>(List.of(space), pageable, 1));
        given(seatRepository.countBySpaceId(spaceId)).willReturn(10L);
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(spaceId)).willReturn(7L);
        given(reservationRepository.countOccupiedEnabledBySpaceId(eq(spaceId), any())).willReturn(2L);

        PageResponse<SpaceListItemResponse> response = favoriteService.getMyFavorites(userId, pageable);

        assertThat(response.content()).hasSize(1);
        SpaceListItemResponse item = response.content().get(0);
        assertThat(item.id()).isEqualTo(spaceId.toString());
        assertThat(item.totalSeats()).isEqualTo(10);
        assertThat(item.availableSeats()).isEqualTo(5);
        assertThat(item.congestion()).isEqualTo("LOW");
    }
}
