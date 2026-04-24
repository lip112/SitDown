package com.univsitdown.space.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.service.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SpaceController.class)
class SpaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpaceService spaceService;

    private static final SpaceListItemResponse SAMPLE_LIST_ITEM = new SpaceListItemResponse(
            UUID.randomUUID().toString(), "제1열람실", 3, "READING_ROOM",
            0, 0, "LOW", "06:00", "22:00",
            List.of("콘센트"), null
    );

    private static final SpaceDetailResponse SAMPLE_DETAIL = new SpaceDetailResponse(
            UUID.randomUUID().toString(), "제1열람실", 3, "READING_ROOM",
            0, 0, 0, 0, "LOW", "06:00", "22:00",
            4, List.of("콘센트"), List.of(), false
    );

    @Test
    @WithMockUser
    void getSpaces_200() throws Exception {
        given(spaceService.getSpaces(any(), any(), any()))
                .willReturn(new PageResponse<>(List.of(SAMPLE_LIST_ITEM), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("제1열람실"))
                .andExpect(jsonPath("$.content[0].congestion").value("LOW"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void getSpace_200() throws Exception {
        UUID id = UUID.randomUUID();
        given(spaceService.getSpace(id)).willReturn(SAMPLE_DETAIL);

        mockMvc.perform(get("/api/spaces/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("제1열람실"))
                .andExpect(jsonPath("$.isFavorite").value(false));
    }

    @Test
    @WithMockUser
    void getSpace_없는ID_404() throws Exception {
        UUID id = UUID.randomUUID();
        given(spaceService.getSpace(id)).willThrow(new SpaceNotFoundException());

        mockMvc.perform(get("/api/spaces/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE-001"));
    }
}
