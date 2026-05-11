package com.univsitdown.stat.service;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.stat.dto.StatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StatServiceTest {

    @Mock
    ReservationRepository reservationRepository;

    @InjectMocks
    StatService statService;

    @Test
    void getStat_WEEKLY_정상반환() {
        given(reservationRepository.findDailyMinutes(any(), any(), any(), any()))
                .willReturn(List.<Object[]>of(new Object[]{"2026-05-05", 90L}));
        given(reservationRepository.findTopSpaces(any(), any(), any(), any()))
                .willReturn(List.<Object[]>of(new Object[]{"space-id", "제1열람실", 90L}));

        StatResponse result = statService.getStat(UUID.randomUUID(), "WEEKLY");

        assertThat(result.period()).isEqualTo("WEEKLY");
        assertThat(result.totalMinutes()).isEqualTo(90L);
        assertThat(result.topSpaces()).hasSize(1);
    }

    @Test
    void getStat_MONTHLY_정상반환() {
        given(reservationRepository.findDailyMinutes(any(), any(), any(), any()))
                .willReturn(List.of());
        given(reservationRepository.findTopSpaces(any(), any(), any(), any()))
                .willReturn(List.of());

        StatResponse result = statService.getStat(UUID.randomUUID(), "MONTHLY");
        assertThat(result.period()).isEqualTo("MONTHLY");
        assertThat(result.totalMinutes()).isZero();
    }

    @Test
    void getStat_잘못된period_예외() {
        assertThatThrownBy(() -> statService.getStat(UUID.randomUUID(), "INVALID"))
                .isInstanceOf(BusinessException.class);
    }
}
