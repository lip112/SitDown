package com.univsitdown.stat.service;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.stat.dto.StatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StatServiceTest {

    @Mock
    ReservationRepository reservationRepository;

    @InjectMocks
    StatService statService;

    @Test
    void getStat_사용자지정기간_정상반환() {
        UUID userId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 3);

        given(reservationRepository.findDailyMinutes(eq(userId), any(), any(), any()))
                .willReturn(List.<Object[]>of(new Object[]{"2026-05-02", 180L}))
                .willReturn(List.<Object[]>of(new Object[]{"2026-04-30", 120L}));
        given(reservationRepository.findTopSpaces(eq(userId), any(), any(), any()))
                .willReturn(List.<Object[]>of(new Object[]{"space-id", "제1열람실", 180L}));

        StatResponse result = statService.getStat(userId, from, to);

        assertThat(result.from()).isEqualTo("2026-05-01");
        assertThat(result.to()).isEqualTo("2026-05-03");
        assertThat(result.totalMinutes()).isEqualTo(180L);
        assertThat(result.comparedToPreviousMinutes()).isEqualTo(60L);
        verify(reservationRepository).findDailyMinutes(eq(userId),
                eq(LocalDateTime.of(2026, 5, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 4, 0, 0)),
                any());
        verify(reservationRepository).findDailyMinutes(eq(userId),
                eq(LocalDateTime.of(2026, 4, 28, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 1, 0, 0)),
                any());
    }

    @Test
    void getStat_사용자지정기간_from이to보다늦으면_예외() {
        assertThatThrownBy(() -> statService.getStat(
                UUID.randomUUID(),
                LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 5, 3)))
                .isInstanceOf(BusinessException.class);
    }
}
