package com.univsitdown.admin.service;

import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.repository.SpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock SpaceRepository spaceRepository;
    @Mock ReservationRepository reservationRepository;
    @InjectMocks AdminDashboardService adminDashboardService;

    @Test
    void getDashboard_공간수와_활성예약수를_반환한다() {
        given(spaceRepository.count()).willReturn(6L);
        given(reservationRepository.countActive(any())).willReturn(3L);

        var response = adminDashboardService.getDashboard();

        assertThat(response.spaceCount()).isEqualTo(6);
        assertThat(response.activeReservationCount()).isEqualTo(3);
    }
}
