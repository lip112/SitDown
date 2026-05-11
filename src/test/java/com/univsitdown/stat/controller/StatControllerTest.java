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
    void getMyStat_200() throws Exception {
        given(statService.getStat(eq(TEST_USER_ID), eq("WEEKLY")))
                .willReturn(new StatResponse("WEEKLY", "2026-05-04", "2026-05-10",
                        90L, 10L, List.of(), List.of()));

        mockMvc.perform(get("/api/stats/me")
                        .param("period", "WEEKLY")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("WEEKLY"))
                .andExpect(jsonPath("$.totalMinutes").value(90));
    }

    @Test
    void getMyStat_토큰없으면_401() throws Exception {
        mockMvc.perform(get("/api/stats/me"))
                .andExpect(status().isUnauthorized());
    }
}
