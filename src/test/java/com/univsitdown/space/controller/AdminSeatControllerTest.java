package com.univsitdown.space.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.space.dto.CreateSeatGridRequest;
import com.univsitdown.space.dto.CreateSeatGridResponse;
import com.univsitdown.space.exception.SeatAlreadyExistsException;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.service.SeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSeatController.class)
@Import(SecurityConfig.class)
class AdminSeatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean SeatService seatService;
    @MockBean JwtProvider jwtProvider;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGrid_200() throws Exception {
        UUID spaceId = UUID.randomUUID();
        given(seatService.createGrid(any(), any()))
                .willReturn(new CreateSeatGridResponse(spaceId.toString(), 80, 8, 10));

        mockMvc.perform(post("/api/admin/spaces/{id}/seats/grid", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSeatGridRequest(8, 10, "A", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(80))
                .andExpect(jsonPath("$.rows").value(8));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGrid_이미존재_409() throws Exception {
        UUID spaceId = UUID.randomUUID();
        given(seatService.createGrid(any(), any())).willThrow(new SeatAlreadyExistsException());

        mockMvc.perform(post("/api/admin/spaces/{id}/seats/grid", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSeatGridRequest(8, 10, "A", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN-002"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGrid_rows_초과_400() throws Exception {
        UUID spaceId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/spaces/{id}/seats/grid", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSeatGridRequest(21, 10, "A", false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSeatStatus_200() throws Exception {
        UUID seatId = UUID.randomUUID();
        willDoNothing().given(seatService).updateSeatStatus(any(), any(Boolean.class));

        mockMvc.perform(patch("/api/admin/seats/{id}", seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isEnabled\": false}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSeatStatus_없는좌석_404() throws Exception {
        UUID seatId = UUID.randomUUID();
        willThrow(new com.univsitdown.space.exception.SeatNotFoundException())
                .given(seatService).updateSeatStatus(any(), any(Boolean.class));

        mockMvc.perform(patch("/api/admin/seats/{id}", seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isEnabled\": false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEAT-001"));
    }
}
