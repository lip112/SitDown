package com.univsitdown.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.exception.UserNotFoundException;
import com.univsitdown.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UserResponse SAMPLE_RESPONSE = new UserResponse(
            TEST_USER_ID.toString(), "test@univ.com", "김학생",
            "010-1234-5678", "학생", null, "USER", "2026-04-22T09:00:00Z"
    );

    @Test
    @WithMockUser
    void getMe_정상_조회_200() throws Exception {
        given(userService.getUser(TEST_USER_ID)).willReturn(SAMPLE_RESPONSE);

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@univ.com"))
                .andExpect(jsonPath("$.name").value("김학생"));
    }

    @Test
    @WithMockUser
    void getMe_존재하지않는_사용자_404() throws Exception {
        given(userService.getUser(TEST_USER_ID)).willThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", TEST_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER-001"));
    }

    @Test
    @WithMockUser
    void updateMe_이름_변경_200() throws Exception {
        UserResponse updated = new UserResponse(
                TEST_USER_ID.toString(), "test@univ.com", "이름변경",
                null, "학생", null, "USER", "2026-04-22T09:00:00Z"
        );
        given(userService.updateUser(eq(TEST_USER_ID), any())).willReturn(updated);

        mockMvc.perform(patch("/api/users/me")
                        .with(csrf())
                        .header("X-User-Id", TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest("이름변경", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이름변경"));
    }

    @Test
    @WithMockUser
    void updateMe_이름_1자_400() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .with(csrf())
                        .header("X-User-Id", TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest("김", null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void updateMe_잘못된_전화번호_형식_400() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .with(csrf())
                        .header("X-User-Id", TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, "01012345678", null))))
                .andExpect(status().isBadRequest());
    }
}
