package com.univsitdown.notice.controller;

import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.response.PageResponse;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.exception.NoticeNotFoundException;
import com.univsitdown.notice.service.NoticeService;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoticeController.class)
@Import(SecurityConfig.class)
class NoticeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    NoticeService noticeService;

    @MockBean
    JwtProvider jwtProvider;

    @Test
    @WithMockUser
    void getNotices_200() throws Exception {
        given(noticeService.getNotices(any(), any()))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser
    void getNotice_없으면_404() throws Exception {
        given(noticeService.getNotice(any())).willThrow(new NoticeNotFoundException());

        mockMvc.perform(get("/api/notices/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTI-001"));
    }
}
