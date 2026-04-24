package com.univsitdown.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.auth.dto.*;
import com.univsitdown.auth.exception.EmailDuplicatedException;
import com.univsitdown.auth.service.AuthService;
import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;
    @MockBean JwtProvider jwtProvider;

    @Test
    void 회원가입_정상() throws Exception {
        SignupRequest request = new SignupRequest(
                "test@univ.com", "P@ssw0rd1!", "홍길동", null, null);
        SignupResponse response = new SignupResponse(
                UUID.randomUUID(), "test@univ.com", "홍길동", "2026-04-24T00:00:00Z");
        given(authService.signup(any())).willReturn(response);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@univ.com"));
    }

    @Test
    void 회원가입_이메일_형식_오류_400() throws Exception {
        SignupRequest request = new SignupRequest(
                "not-an-email", "P@ssw0rd1!", "홍길동", null, null);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 회원가입_이메일_중복_409() throws Exception {
        SignupRequest request = new SignupRequest(
                "test@univ.com", "P@ssw0rd1!", "홍길동", null, null);
        given(authService.signup(any())).willThrow(new EmailDuplicatedException());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH-104"));
    }

    @Test
    void 이메일_코드_발송_정상() throws Exception {
        EmailSendRequest request = new EmailSendRequest("new@univ.com");
        given(authService.sendEmailCode("new@univ.com"))
                .willReturn(new EmailSendResponse("new@univ.com", "2026-04-24T00:03:00Z"));

        mockMvc.perform(post("/api/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@univ.com"));
    }

    @Test
    void 이메일_코드_확인_정상() throws Exception {
        EmailVerifyRequest request = new EmailVerifyRequest("test@univ.com", "123456");
        given(authService.verifyEmailCode("test@univ.com", "123456"))
                .willReturn(new EmailVerifyResponse(true));

        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void 로그인_정상() throws Exception {
        LoginRequest request = new LoginRequest("test@univ.com", "P@ssw0rd1!");
        LoginResponse response = new LoginResponse(
                "access-token", "refresh-token", 1800L,
                new LoginResponse.LoginUserInfo(UUID.randomUUID(), "test@univ.com", "홍길동", "USER"));
        given(authService.login("test@univ.com", "P@ssw0rd1!")).willReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void 토큰_갱신_정상() throws Exception {
        TokenRefreshRequest request = new TokenRefreshRequest("old-refresh");
        given(authService.refresh("old-refresh"))
                .willReturn(new TokenRefreshResponse("new-access", 1800L));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"));
    }

    @Test
    void 로그아웃_204() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }
}
