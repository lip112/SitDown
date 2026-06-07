package com.univsitdown.stat.controller;

import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.stat.dto.StatResponse;
import com.univsitdown.stat.service.StatService;
import com.univsitdown.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatController.class)
@Import(SecurityConfig.class)
class StatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    StatService statService;

    @MockBean
    JwtProvider jwtProvider;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        given(jwtProvider.parse(TOKEN))
                .willReturn(new UserPrincipal(TEST_USER_ID, UserRole.USER));
    }

    @Test
    void getMyStat_fromTo_200() throws Exception {
        given(statService.getStat(eq(TEST_USER_ID),
                eq(LocalDate.of(2026, 5, 1)),
                eq(LocalDate.of(2026, 5, 3))))
                .willReturn(new StatResponse("2026-05-01", "2026-05-03",
                        180L, 60L, List.of(), List.of()));

        mockMvc.perform(get("/api/stats/me")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-03")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").doesNotExist())
                .andExpect(jsonPath("$.from").value("2026-05-01"))
                .andExpect(jsonPath("$.to").value("2026-05-03"))
                .andExpect(jsonPath("$.totalMinutes").value(180));
    }

    @Test
    void getMyStat_fromTo_날짜형식오류_400() throws Exception {
        mockMvc.perform(get("/api/stats/me")
                        .param("from", "2026-05-xx")
                        .param("to", "2026-05-03")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAT-001"));
    }

    @Test
    void getMyStat_period만_보내면_400() throws Exception {
        mockMvc.perform(get("/api/stats/me")
                        .param("period", "WEEKLY")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAT-001"));
    }

    @Test
    void getMyStat_fromTo_없으면_400() throws Exception {
        mockMvc.perform(get("/api/stats/me")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STAT-001"));
    }

    @Test
    void getMyStat_토큰없으면_401() throws Exception {
        mockMvc.perform(get("/api/stats/me"))
                .andExpect(status().isUnauthorized());
    }
}
