package com.univsitdown.space.service;

import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSeatGridRequest;
import com.univsitdown.space.dto.CreateSeatGridResponse;
import com.univsitdown.space.dto.SeatLayoutResponse;
import com.univsitdown.space.exception.SeatAlreadyExistsException;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.Cacheable;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.*;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock SeatRepository seatRepository;
    @Mock SpaceRepository spaceRepository;
    @Mock ReservationRepository reservationRepository;
    @InjectMocks SeatService seatService;

    private Space sampleSpace() {
        return Space.create("제1열람실", 3, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4, List.of(), null);
    }

    // --- createGrid ---

    @Test
    void createGrid_정상생성_성공() {
        UUID spaceId = UUID.randomUUID();
        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(sampleSpace()));
        given(seatRepository.existsBySpaceId(spaceId)).willReturn(false);
        given(seatRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        CreateSeatGridResponse response = seatService.createGrid(spaceId, new CreateSeatGridRequest(2, 3, "A", false));

        assertThat(response.createdCount()).isEqualTo(6);
        assertThat(response.rows()).isEqualTo(2);
        assertThat(response.columns()).isEqualTo(3);
    }

    @Test
    void createGrid_좌석이미존재하고overwrite_false_예외() {
        UUID spaceId = UUID.randomUUID();
        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(sampleSpace()));
        given(seatRepository.existsBySpaceId(spaceId)).willReturn(true);

        assertThatThrownBy(() -> seatService.createGrid(spaceId, new CreateSeatGridRequest(2, 3, "A", false)))
                .isInstanceOf(SeatAlreadyExistsException.class);
    }

    @Test
    void createGrid_overwrite_true_기존삭제후재생성() {
        UUID spaceId = UUID.randomUUID();
        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(sampleSpace()));
        given(seatRepository.existsBySpaceId(spaceId)).willReturn(true);
        given(seatRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        CreateSeatGridResponse response = seatService.createGrid(spaceId, new CreateSeatGridRequest(2, 3, "A", true));

        then(seatRepository).should().deleteBySpaceId(spaceId);
        assertThat(response.createdCount()).isEqualTo(6);
    }

    @Test
    void createGrid_없는공간_SpaceNotFoundException() {
        UUID spaceId = UUID.randomUUID();
        given(spaceRepository.findById(spaceId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> seatService.createGrid(spaceId, new CreateSeatGridRequest(2, 3, "A", false)))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    // --- updateSeatStatus ---

    @Test
    void updateSeatStatus_비활성화_성공() {
        UUID seatId = UUID.randomUUID();
        Seat seat = Seat.create(sampleSpace(), 1, 1, "A-1");
        given(seatRepository.findById(seatId)).willReturn(Optional.of(seat));

        seatService.updateSeatStatus(seatId, false);

        assertThat(seat.isEnabled()).isFalse();
    }

    @Test
    void updateSeatStatus_없는좌석_SeatNotFoundException() {
        UUID seatId = UUID.randomUUID();
        given(seatRepository.findById(seatId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> seatService.updateSeatStatus(seatId, false))
                .isInstanceOf(SeatNotFoundException.class);
    }

    // --- getSeatLayout ---

    @Test
    void getSeatLayout_at에_따라_달라지는_좌석상태는_캐시하지_않음() throws NoSuchMethodException {
        assertThat(SeatService.class
                .getMethod("getSeatLayout", UUID.class, LocalDateTime.class)
                .isAnnotationPresent(Cacheable.class)).isFalse();
    }

    @Test
    void getSeatLayout_정상조회_AVAILABLE() {
        UUID spaceId = UUID.randomUUID();
        Space space = sampleSpace();
        Seat seat = Seat.create(space, 1, 1, "A-1");
        ReflectionTestUtils.setField(seat, "id", UUID.randomUUID());
        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(space));
        given(seatRepository.findBySpaceIdOrderByRowNumAscColNumAsc(spaceId)).willReturn(List.of(seat));
        given(reservationRepository.findOccupiedSeatIdsBySpaceId(any(), any())).willReturn(List.of());

        SeatLayoutResponse response = seatService.getSeatLayout(spaceId, LocalDateTime.now());

        assertThat(response.seats()).hasSize(1);
        assertThat(response.seats().get(0).status()).isEqualTo("AVAILABLE");
    }

    @Test
    void getSeatLayout_없는공간_SpaceNotFoundException() {
        UUID spaceId = UUID.randomUUID();
        given(spaceRepository.findById(spaceId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> seatService.getSeatLayout(spaceId, LocalDateTime.now()))
                .isInstanceOf(SpaceNotFoundException.class);
    }
}
