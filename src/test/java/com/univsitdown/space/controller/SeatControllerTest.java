package com.univsitdown.space.controller;

import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.space.dto.SeatDetailResponse;
import com.univsitdown.space.dto.SeatItemResponse;
import com.univsitdown.space.dto.SeatLayoutResponse;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.service.SeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SeatController.class)
@Import(SecurityConfig.class)
class SeatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SeatService seatService;
    @MockBean JwtProvider jwtProvider;

    @Test
    @WithMockUser
    void getSeatLayout_200() throws Exception {
        UUID spaceId = UUID.randomUUID();
        SeatItemResponse item = new SeatItemResponse(UUID.randomUUID().toString(), "A-1", 1, 1, "AVAILABLE", List.of());
        given(seatService.getSeatLayout(any(), any()))
                .willReturn(new SeatLayoutResponse(spaceId.toString(), 1, 1, List.of(item)));

        mockMvc.perform(get("/api/spaces/{id}/seats", spaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").value(1))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"));
    }

    @Test
    @WithMockUser
    void getSeatLayout_없는공간_404() throws Exception {
        given(seatService.getSeatLayout(any(), any())).willThrow(new SpaceNotFoundException());

        mockMvc.perform(get("/api/spaces/{id}/seats", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE-001"));
    }

    @Test
    @WithMockUser
    void getSeatDetail_200() throws Exception {
        UUID seatId = UUID.randomUUID();
        SeatDetailResponse response = new SeatDetailResponse(
                seatId.toString(), "A-1", 1, 1, "AVAILABLE", List.of(),
                UUID.randomUUID().toString(), "제1열람실");
        given(seatService.getSeatDetail(any(), any())).willReturn(response);

        mockMvc.perform(get("/api/seats/{id}", seatId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("A-1"))
                .andExpect(jsonPath("$.spaceName").value("제1열람실"));
    }

    @Test
    @WithMockUser
    void getSeatDetail_없는좌석_404() throws Exception {
        given(seatService.getSeatDetail(any(), any())).willThrow(new SeatNotFoundException());

        mockMvc.perform(get("/api/seats/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEAT-001"));
    }
}
