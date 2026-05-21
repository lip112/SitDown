package com.univsitdown.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.response.PageResponse;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.exception.UserNotFoundException;
import com.univsitdown.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserService userService;
    @MockBean JwtProvider jwtProvider;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UserResponse SAMPLE_RESPONSE = new UserResponse(
            USER_ID.toString(), "test@univ.com", "김학생",
            "010-1234-5678", "UNDERGRADUATE", null, "USER", "2026-04-22 09:00:00"
    );

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUsers_회원목록_200() throws Exception {
        given(userService.getUsers(any(PageRequest.class)))
                .willReturn(new PageResponse<>(List.of(SAMPLE_RESPONSE), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("test@univ.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUser_회원상세_200() throws Exception {
        given(userService.getUser(USER_ID)).willReturn(SAMPLE_RESPONSE);

        mockMvc.perform(get("/api/admin/users/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.name").value("김학생"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUser_회원정보수정_200() throws Exception {
        UserResponse updated = new UserResponse(
                USER_ID.toString(), "test@univ.com", "이름변경",
                "010-1234-5678", "UNDERGRADUATE", null, "USER", "2026-04-22 09:00:00"
        );
        given(userService.updateUser(eq(USER_ID), any(UpdateUserRequest.class))).willReturn(updated);

        mockMvc.perform(patch("/api/admin/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest("이름변경", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이름변경"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUser_잘못된_전화번호_400() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, "01012345678", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-100"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUser_회원삭제_204() throws Exception {
        willDoNothing().given(userService).deleteUser(USER_ID);

        mockMvc.perform(delete("/api/admin/users/{id}", USER_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteUser_없는회원_404() throws Exception {
        willThrow(new UserNotFoundException()).given(userService).deleteUser(USER_ID);

        mockMvc.perform(delete("/api/admin/users/{id}", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER-001"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminUsers_일반사용자_403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }
}
