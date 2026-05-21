package com.univsitdown.notice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.notice.domain.NoticeCategory;
import com.univsitdown.notice.dto.CreateNoticeRequest;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.UpdateNoticeRequest;
import com.univsitdown.notice.exception.NoticeNotFoundException;
import com.univsitdown.notice.service.NoticeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminNoticeController.class)
@Import(SecurityConfig.class)
class AdminNoticeControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean NoticeService noticeService;
    @MockBean JwtProvider jwtProvider;

    private static final UUID NOTICE_ID = UUID.randomUUID();
    private static final NoticeDetailResponse SAMPLE_RESPONSE = new NoticeDetailResponse(
            NOTICE_ID.toString(),
            "공지 제목",
            "공지 내용",
            "INFO",
            "2026-05-14 09:00:00"
    );

    @Test
    @WithMockUser(roles = "ADMIN")
    void createNotice_공지생성_201() throws Exception {
        given(noticeService.createNotice(any())).willReturn(SAMPLE_RESPONSE);
        CreateNoticeRequest request = new CreateNoticeRequest(
                "공지 제목",
                "공지 내용",
                NoticeCategory.INFO,
                Instant.parse("2026-05-14T00:00:00Z"),
                null
        );

        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("공지 제목"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createNotice_제목누락_400() throws Exception {
        String requestJson = """
                {
                  "content": "공지 내용",
                  "category": "INFO",
                  "publishedAt": "2026-05-14T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-100"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateNotice_공지수정_200() throws Exception {
        NoticeDetailResponse updated = new NoticeDetailResponse(
                NOTICE_ID.toString(),
                "수정 제목",
                "공지 내용",
                "EVENT",
                "2026-05-14 09:00:00"
        );
        given(noticeService.updateNotice(eq(NOTICE_ID), any())).willReturn(updated);
        UpdateNoticeRequest request = new UpdateNoticeRequest("수정 제목", null, NoticeCategory.EVENT, null, null);

        mockMvc.perform(patch("/api/admin/notices/{id}", NOTICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정 제목"))
                .andExpect(jsonPath("$.category").value("EVENT"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateNotice_없는공지_404() throws Exception {
        given(noticeService.updateNotice(eq(NOTICE_ID), any())).willThrow(new NoticeNotFoundException());

        mockMvc.perform(patch("/api/admin/notices/{id}", NOTICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateNoticeRequest("수정 제목", null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTI-001"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteNotice_공지삭제_204() throws Exception {
        willDoNothing().given(noticeService).deleteNotice(NOTICE_ID);

        mockMvc.perform(delete("/api/admin/notices/{id}", NOTICE_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteNotice_없는공지_404() throws Exception {
        willThrow(new NoticeNotFoundException()).given(noticeService).deleteNotice(NOTICE_ID);

        mockMvc.perform(delete("/api/admin/notices/{id}", NOTICE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTI-001"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminNotices_일반사용자_403() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
