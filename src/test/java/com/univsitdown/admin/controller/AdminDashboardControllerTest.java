package com.univsitdown.admin.controller;

import com.univsitdown.admin.dto.AdminDashboardResponse;
import com.univsitdown.admin.service.AdminDashboardService;
import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
@Import(SecurityConfig.class)
class AdminDashboardControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AdminDashboardService adminDashboardService;
    @MockBean JwtProvider jwtProvider;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDashboard_관리자_대시보드_지표_조회_200() throws Exception {
        given(adminDashboardService.getDashboard())
                .willReturn(new AdminDashboardResponse(6, 3));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceCount").value(6))
                .andExpect(jsonPath("$.activeReservationCount").value(3));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDashboard_일반사용자_403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isForbidden());
    }
}
