package com.univsitdown.admin.service;

import com.univsitdown.admin.dto.AdminDashboardResponse;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        return new AdminDashboardResponse(
                spaceRepository.count(),
                reservationRepository.countActive(now)
        );
    }
}
