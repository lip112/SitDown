package com.univsitdown.reservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.response.PageResponse;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.reservation.dto.*;
import com.univsitdown.reservation.exception.*;
import com.univsitdown.reservation.service.ReservationService;
import com.univsitdown.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ReservationService reservationService;
    @MockBean JwtProvider jwtProvider;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TOKEN = "test-token";
    private static final String SEAT_ID = UUID.randomUUID().toString();
    private static final String RSV_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        given(jwtProvider.parse(TOKEN)).willReturn(new UserPrincipal(USER_ID, UserRole.USER));
    }

    @Test
    void reserve_201() throws Exception {
        CreateReservationResponse response = new CreateReservationResponse(
                RSV_ID, SEAT_ID, "A-1", UUID.randomUUID().toString(), "제1열람실",
                "2026-05-01 09:00:00", "2026-05-01 11:00:00", 2, "SCHEDULED", "2026-05-01 08:55:00"
        );
        given(reservationService.reserve(any(), any())).willReturn(response);

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                UUID.fromString(SEAT_ID),
                                LocalDateTime.of(2026, 5, 1, 9, 0),
                                LocalDateTime.of(2026, 5, 1, 11, 0)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatLabel").value("A-1"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void reserve_중복예약_409() throws Exception {
        given(reservationService.reserve(any(), any())).willThrow(new SeatAlreadyReservedException());

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                UUID.fromString(SEAT_ID),
                                LocalDateTime.of(2026, 5, 1, 9, 0),
                                LocalDateTime.of(2026, 5, 1, 11, 0)
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RSV-004"));
    }

    @Test
    void getMyReservations_200() throws Exception {
        ReservationListItemResponse item = new ReservationListItemResponse(
                RSV_ID, "A-1", "제1열람실", 3,
                "2026-05-01 09:00:00", "2026-05-01 11:00:00", "SCHEDULED", null
        );
        given(reservationService.getMyReservations(any(), any(), any()))
                .willReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/reservations/me")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].seatLabel").value("A-1"));
    }

    @Test
    void getReservation_200() throws Exception {
        UUID rsvId = UUID.randomUUID();
        ReservationDetailResponse response = new ReservationDetailResponse(
                rsvId.toString(), SEAT_ID, "A-1", UUID.randomUUID().toString(), "제1열람실", 3,
                "2026-05-01 09:00:00", "2026-05-01 11:00:00", 2, "SCHEDULED", null, 0, "2026-05-01 08:55:00"
        );
        given(reservationService.getReservation(any(), any())).willReturn(response);

        mockMvc.perform(get("/api/reservations/{id}", rsvId)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatLabel").value("A-1"));
    }

    @Test
    void getReservation_없는예약_404() throws Exception {
        given(reservationService.getReservation(any(), any())).willThrow(new ReservationNotFoundException());

        mockMvc.perform(get("/api/reservations/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RSV-031"));
    }

    @Test
    void extend_200() throws Exception {
        UUID rsvId = UUID.randomUUID();
        ExtendReservationResponse response = new ExtendReservationResponse(rsvId.toString(), "2026-05-01 12:00:00", 1);
        given(reservationService.extend(any(), any(), any(Integer.class))).willReturn(response);

        mockMvc.perform(patch("/api/reservations/{id}/extend", rsvId)
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"additionalMinutes\": 60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extendedCount").value(1));
    }

    @Test
    void cancel_204() throws Exception {
        UUID rsvId = UUID.randomUUID();
        willDoNothing().given(reservationService).cancel(any(), any());

        mockMvc.perform(delete("/api/reservations/{id}", rsvId)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNoContent());
    }
}
