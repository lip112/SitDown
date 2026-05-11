package com.univsitdown.space.service;

import com.univsitdown.space.domain.UserFavorite;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.space.repository.UserFavoriteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    UserFavoriteRepository userFavoriteRepository;

    @Mock
    SpaceRepository spaceRepository;

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
}
