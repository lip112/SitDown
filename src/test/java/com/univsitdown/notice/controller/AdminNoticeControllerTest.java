package com.univsitdown.notice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.response.PageResponse;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.notice.domain.NoticeCategory;
import com.univsitdown.notice.dto.CreateNoticeRequest;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.dto.UpdateNoticeRequest;
import com.univsitdown.notice.exception.NoticeNotFoundException;
import com.univsitdown.notice.service.NoticeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
            "2026-05-14 09:00:00",
            null,
            true
    );

    @Test
    @WithMockUser(roles = "ADMIN")
    void getNotices_관리자목록_200() throws Exception {
        NoticeListItemResponse item = new NoticeListItemResponse(
                NOTICE_ID.toString(),
                "예약 공지",
                "INFO",
                "2026-05-15 09:00:00",
                "2026-05-16 09:00:00",
                true
        );
        given(noticeService.getAdminNotices(any(), any()))
                .willReturn(new PageResponse<>(List.of(item), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/admin/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].expiresAt").value("2026-05-16 09:00:00"))
                .andExpect(jsonPath("$.content[0].isNew").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getNotice_관리자상세_200() throws Exception {
        given(noticeService.getAdminNotice(NOTICE_ID)).willReturn(SAMPLE_RESPONSE);

        mockMvc.perform(get("/api/admin/notices/{id}", NOTICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NOTICE_ID.toString()))
                .andExpect(jsonPath("$.isNew").value(true));
    }

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
                "2026-05-14 09:00:00",
                null,
                true
        );
        given(noticeService.updateNotice(eq(NOTICE_ID), any())).willReturn(updated);

        mockMvc.perform(patch("/api/admin/notices/{id}", NOTICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정 제목",
                                  "category": "EVENT",
                                  "expiresAt": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정 제목"))
                .andExpect(jsonPath("$.category").value("EVENT"));

        ArgumentCaptor<UpdateNoticeRequest> captor = ArgumentCaptor.forClass(UpdateNoticeRequest.class);
        then(noticeService).should().updateNotice(eq(NOTICE_ID), captor.capture());
        assertThat(captor.getValue().isExpiresAtProvided()).isTrue();
        assertThat(captor.getValue().expiresAt()).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateNotice_없는공지_404() throws Exception {
        given(noticeService.updateNotice(eq(NOTICE_ID), any())).willThrow(new NoticeNotFoundException());

        mockMvc.perform(patch("/api/admin/notices/{id}", NOTICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "수정 제목"}
                                """))
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
