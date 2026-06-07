package com.univsitdown.user.controller;

import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.space.service.FavoriteService;
import com.univsitdown.user.domain.UserRole;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class ProfileImageControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UserService userService;

    @MockBean
    FavoriteService favoriteService;

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
    void uploadProfileImage_200() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "profile.jpg", "image/jpeg", "test-image".getBytes());

        given(userService.updateProfileImage(any(), any()))
                .willReturn(new UserResponse(UUID.randomUUID().toString(), "test@test.com",
                        "테스터", null, null, "/uploads/profiles/test/file.jpg", "USER",
                        Instant.now().toString()));

        mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(mockFile)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value("/uploads/profiles/test/file.jpg"));
    }

    @Test
    void uploadProfileImage_토큰없으면_401() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "profile.jpg", "image/jpeg", "test-image".getBytes());

        mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(mockFile))
                .andExpect(status().isUnauthorized());
    }
}
