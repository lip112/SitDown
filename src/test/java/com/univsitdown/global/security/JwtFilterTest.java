package com.univsitdown.global.security;

import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.controller.HealthController;
import com.univsitdown.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HealthController.class)
@Import(SecurityConfig.class)
class JwtFilterTest {

    @Autowired MockMvc mockMvc;
    @MockBean JwtProvider jwtProvider;
    @MockBean DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        Connection connection = mock(Connection.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(anyInt())).willReturn(true);
    }

    @Test
    void 유효한_토큰으로_공개_엔드포인트_접근_성공() throws Exception {
        UUID userId = UUID.randomUUID();
        given(jwtProvider.parse("valid-token"))
                .willReturn(new UserPrincipal(userId, UserRole.USER));

        mockMvc.perform(get("/api/health")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }

    @Test
    void 토큰_없이_공개_엔드포인트_접근_성공() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
