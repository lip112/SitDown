package com.univsitdown.space.service;

import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CongestionPredictionResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CongestionPredictionTest {

    @Mock SpaceRepository spaceRepository;
    @Mock SeatRepository seatRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock FavoriteService favoriteService;
    @InjectMocks SpaceService spaceService;

    @Test
    void getCongestionPrediction_공간없으면_예외() {
        given(spaceRepository.findById(any())).willReturn(Optional.empty());
        assertThatThrownBy(() -> spaceService.getCongestionPrediction(UUID.randomUUID(), LocalDate.now()))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void getCongestionPrediction_정상반환() {
        Space space = buildSpace(9, 18);
        given(spaceRepository.findById(any())).willReturn(Optional.of(space));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(100L);
        given(reservationRepository.countOccupiedAtSlot(any(), any(), any())).willReturn(30L);

        CongestionPredictionResponse result =
                spaceService.getCongestionPrediction(UUID.randomUUID(), LocalDate.now());

        assertThat(result.hourly()).hasSize(9); // 09~17시 9개
        assertThat(result.hourly().get(0).occupancyRate()).isEqualTo(0.30);
        assertThat(result.hourly().get(0).level()).isEqualTo("LOW");
    }

    @Test
    void getCongestionPrediction_좌석없으면_모두LOW() {
        Space space = buildSpace(9, 11);
        given(spaceRepository.findById(any())).willReturn(Optional.of(space));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(0L);
        given(reservationRepository.countOccupiedAtSlot(any(), any(), any())).willReturn(0L);

        CongestionPredictionResponse result =
                spaceService.getCongestionPrediction(UUID.randomUUID(), LocalDate.now());

        assertThat(result.hourly()).allMatch(h -> "LOW".equals(h.level()));
    }

    private Space buildSpace(int openHour, int closeHour) {
        return Space.create("테스트 공간", 1,
                SpaceCategory.READING_ROOM,
                LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0),
                4, List.of(), null);
    }
}
