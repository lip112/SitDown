package com.univsitdown.reservation.service;

import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;
import com.univsitdown.reservation.dto.*;
import com.univsitdown.reservation.exception.*;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.exception.SeatUnavailableException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.user.domain.User;
import com.univsitdown.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservationRepository;
    @Mock SeatRepository seatRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ReservationService reservationService;

    private UUID userId;
    private UUID seatId;
    private Space space;
    private Seat seat;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        seatId = UUID.randomUUID();
        space = Space.create("제1열람실", 3, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4, List.of(), null);
        ReflectionTestUtils.setField(space, "id", UUID.randomUUID());
        seat = Seat.create(space, 1, 1, "A-1");
        ReflectionTestUtils.setField(seat, "id", seatId);
        user = User.create("test@test.com", "hash", "테스터", null, null);
        ReflectionTestUtils.setField(user, "id", userId);
    }

    private CreateReservationRequest validRequest() {
        return new CreateReservationRequest(
                seatId,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 1, 11, 0)
        );
    }

    // --- reserve ---

    @Test
    void reserve_정상예약_성공() {
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));
        given(reservationRepository.countActiveByUserId(any(), any())).willReturn(0L);
        given(reservationRepository.existsOverlapping(any(), any(), any())).willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(reservationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        CreateReservationResponse response = reservationService.reserve(userId, validRequest());

        assertThat(response.seatLabel()).isEqualTo("A-1");
        assertThat(response.status()).isEqualTo("SCHEDULED");
        assertThat(response.durationHours()).isEqualTo(2);
    }

    @Test
    void reserve_종료시간이_시작시간보다_이른경우_예외() {
        CreateReservationRequest bad = new CreateReservationRequest(
                seatId,
                LocalDateTime.of(2026, 5, 1, 11, 0),
                LocalDateTime.of(2026, 5, 1, 9, 0)
        );
        assertThatThrownBy(() -> reservationService.reserve(userId, bad))
                .isInstanceOf(ReservationInvalidTimeException.class);
    }

    @Test
    void reserve_없는좌석_SeatNotFoundException() {
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> reservationService.reserve(userId, validRequest()))
                .isInstanceOf(SeatNotFoundException.class);
    }

    @Test
    void reserve_비활성화좌석_SeatUnavailableException() {
        seat.updateEnabled(false);
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));
        assertThatThrownBy(() -> reservationService.reserve(userId, validRequest()))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void reserve_최대이용시간초과_ReservationMaxDurationExceededException() {
        CreateReservationRequest bad = new CreateReservationRequest(
                seatId,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 1, 14, 0)
        );
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));
        assertThatThrownBy(() -> reservationService.reserve(userId, bad))
                .isInstanceOf(ReservationMaxDurationExceededException.class);
    }

    @Test
    void reserve_활성예약이미존재_UserReservationLimitException() {
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));
        given(reservationRepository.countActiveByUserId(any(), any())).willReturn(1L);
        assertThatThrownBy(() -> reservationService.reserve(userId, validRequest()))
                .isInstanceOf(UserReservationLimitException.class);
    }

    @Test
    void reserve_좌석중복예약_SeatAlreadyReservedException() {
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));
        given(reservationRepository.countActiveByUserId(any(), any())).willReturn(0L);
        given(reservationRepository.existsOverlapping(any(), any(), any())).willReturn(true);
        assertThatThrownBy(() -> reservationService.reserve(userId, validRequest()))
                .isInstanceOf(SeatAlreadyReservedException.class);
    }

    // --- getReservation ---

    @Test
    void getReservation_정상조회_성공() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.create(user, seat,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 1, 11, 0));
        ReflectionTestUtils.setField(reservation, "id", reservationId);
        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

        ReservationDetailResponse response = reservationService.getReservation(reservationId, userId);

        assertThat(response.seatLabel()).isEqualTo("A-1");
        assertThat(response.status()).isEqualTo("SCHEDULED");
    }

    @Test
    void getReservation_본인아닌예약_ReservationNotOwnerException() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = Reservation.create(user, seat,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 1, 11, 0));
        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.getReservation(reservationId, UUID.randomUUID()))
                .isInstanceOf(ReservationNotOwnerException.class);
    }

    @Test
    void getReservation_없는예약_ReservationNotFoundException() {
        UUID reservationId = UUID.randomUUID();
        given(reservationRepository.findById(reservationId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.getReservation(reservationId, userId))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    // --- extend ---

    private Reservation inUseReservation() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        return Reservation.create(user, seat,
                now.minusHours(1),
                now.plusHours(1));
    }

    @Test
    void extend_정상연장_성공() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = inUseReservation();
        ReflectionTestUtils.setField(reservation, "id", reservationId);
        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
        given(reservationRepository.existsOverlappingExclude(any(), any(), any(), any())).willReturn(false);

        ExtendReservationResponse response = reservationService.extend(reservationId, userId, 30);

        assertThat(response.extendedCount()).isEqualTo(1);
    }

    @Test
    void extend_SCHEDULED상태_ReservationNotExtendableException() {
        UUID reservationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        Reservation reservation = Reservation.create(user, seat,
                now.plusHours(1),
                now.plusHours(3));
        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.extend(reservationId, userId, 30))
                .isInstanceOf(ReservationNotExtendableException.class);
    }

    @Test
    void extend_후속예약충돌_ReservationExtendConflictException() {
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = inUseReservation();
        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
        given(reservationRepository.existsOverlappingExclude(any(), any(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> reservationService.extend(reservationId, userId, 30))
                .isInstanceOf(ReservationExtendConflictException.class);
    }

    // --- cancel ---

    @Test
    void cancel_정상취소_성공() {
        UUID reservationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        Reservation reservation = Reservation.create(user, seat,
                now.plusHours(1),
                now.plusHours(3));
        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

        reservationService.cancel(reservationId, userId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(reservation.getCanceledAt()).isNotNull();
    }

    @Test
    void cancel_이미종료된예약_ReservationAlreadyEndedException() {
        UUID reservationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        Reservation reservation = Reservation.create(user, seat,
                now.minusHours(3),
                now.minusHours(1));
        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(reservationId, userId))
                .isInstanceOf(ReservationAlreadyEndedException.class);
    }

    @Test
    void cancel_본인아닌예약_ReservationNotOwnerException() {
        UUID reservationId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        Reservation reservation = Reservation.create(user, seat,
                now.plusHours(1),
                now.plusHours(3));
        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(reservationId, UUID.randomUUID()))
                .isInstanceOf(ReservationNotOwnerException.class);
    }
}
