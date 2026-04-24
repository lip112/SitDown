package com.univsitdown.space.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.service.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSpaceController.class)
class AdminSpaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpaceService spaceService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final SpaceDetailResponse SAMPLE_DETAIL = new SpaceDetailResponse(
            "test-space-id", "제2열람실", 2, "READING_ROOM",
            0, 0, 0, 0, "LOW", "06:00:00", "22:00:00",
            4, List.of("와이파이"), List.of(), false
    );

    // Phase 3 전: JWT 인증 미구현으로 @WithMockUser(USER)로 테스트. Phase 3 이후 ADMIN 역할 검증 추가 예정
    @Test
    @WithMockUser
    void createSpace_201() throws Exception {
        given(spaceService.createSpace(any())).willReturn(SAMPLE_DETAIL);

        CreateSpaceRequest request = new CreateSpaceRequest(
                "제2열람실", 2, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4,
                List.of("와이파이"), null
        );

        mockMvc.perform(post("/api/admin/spaces")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("제2열람실"))
                .andExpect(jsonPath("$.floor").value(2))
                .andExpect(jsonPath("$.category").value("READING_ROOM"));
    }

    @Test
    @WithMockUser
    void createSpace_name_누락_400() throws Exception {
        String requestJson = """
                {
                  "floor": 2,
                  "category": "READING_ROOM",
                  "openTime": "06:00:00",
                  "closeTime": "22:00:00",
                  "maxReservationHours": 4
                }
                """;

        mockMvc.perform(post("/api/admin/spaces")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-100"));
    }
}
