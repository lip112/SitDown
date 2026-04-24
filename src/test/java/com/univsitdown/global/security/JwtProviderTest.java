package com.univsitdown.global.security;

import com.univsitdown.user.domain.UserRole;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                "test-secret-key-that-is-at-least-32-chars-long-for-hmac",
                1800L
        );
    }

    @Test
    void 토큰_생성_후_파싱하면_동일한_userId와_role을_반환한다() {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.generateAccessToken(userId, UserRole.USER);

        UserPrincipal principal = jwtProvider.parse(token);

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void ADMIN_role로_생성한_토큰을_파싱하면_ADMIN_role이_반환된다() {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.generateAccessToken(userId, UserRole.ADMIN);

        UserPrincipal principal = jwtProvider.parse(token);

        assertThat(principal.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 위조된_토큰을_파싱하면_JwtException이_발생한다() {
        assertThatThrownBy(() -> jwtProvider.parse("invalid.token.value"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 만료된_토큰을_파싱하면_JwtException이_발생한다() {
        JwtProvider shortLivedProvider = new JwtProvider(
                "test-secret-key-that-is-at-least-32-chars-long-for-hmac",
                -1L
        );
        String expiredToken = shortLivedProvider.generateAccessToken(UUID.randomUUID(), UserRole.USER);

        assertThatThrownBy(() -> jwtProvider.parse(expiredToken))
                .isInstanceOf(JwtException.class);
    }
}
