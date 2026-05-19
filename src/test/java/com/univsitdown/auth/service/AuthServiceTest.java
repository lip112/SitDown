package com.univsitdown.auth.service;

import com.univsitdown.auth.dto.*;
import com.univsitdown.auth.exception.*;
import com.univsitdown.global.security.AuthStore;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.user.domain.Affiliation;
import com.univsitdown.user.domain.User;
import com.univsitdown.user.domain.UserRole;
import com.univsitdown.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock AuthStore authStore;
    @InjectMocks AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.create("test@univ.com", "hashed", "홍길동", "010-1234-5678", Affiliation.UNDERGRADUATE);
    }

    @Test
    void 이메일_인증_없이_정상_회원가입() {
        given(userRepository.existsByEmail("test@univ.com")).willReturn(false);
        given(passwordEncoder.encode("Serv1ce$Test")).willReturn("hashed");
        given(userRepository.save(any(User.class))).willReturn(sampleUser);

        SignupRequest request = new SignupRequest("test@univ.com", "Serv1ce$Test", "홍길동", null, null);
        SignupResponse response = authService.signup(request);

        assertThat(response.email()).isEqualTo("test@univ.com");
        assertThat(response.name()).isEqualTo("홍길동");
        then(authStore).shouldHaveNoInteractions();
    }

    @Test
    void 이메일_중복_회원가입_시_예외() {
        given(userRepository.existsByEmail("test@univ.com")).willReturn(true);

        SignupRequest request = new SignupRequest("test@univ.com", "Serv1ce$Test", "홍길동", null, null);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(EmailDuplicatedException.class);
    }

    @Test
    void 사용_가능한_이메일_중복_확인() {
        given(userRepository.existsByEmail("new@univ.com")).willReturn(false);

        EmailCheckResponse response = authService.checkEmail("new@univ.com");

        assertThat(response.email()).isEqualTo("new@univ.com");
        assertThat(response.available()).isTrue();
        then(authStore).shouldHaveNoInteractions();
    }

    @Test
    void 이미_가입된_이메일_중복_확인_시_예외() {
        given(userRepository.existsByEmail("test@univ.com")).willReturn(true);

        assertThatThrownBy(() -> authService.checkEmail("test@univ.com"))
                .isInstanceOf(EmailDuplicatedException.class);
        then(authStore).shouldHaveNoInteractions();
    }

    @Test
    void 정상_로그인() {
        given(userRepository.findByEmail("test@univ.com")).willReturn(Optional.of(sampleUser));
        given(passwordEncoder.matches("Serv1ce$Test", "hashed")).willReturn(true);
        given(jwtProvider.generateAccessToken(any(), eq(UserRole.USER))).willReturn("access-token");
        given(jwtProvider.getAccessTokenExpirySeconds()).willReturn(1800L);

        LoginResponse response = authService.login("test@univ.com", "Serv1ce$Test");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        then(authStore).should().saveRefreshToken(any(), anyString());
    }

    @Test
    void 존재하지_않는_이메일_로그인_시_예외() {
        given(userRepository.findByEmail("none@univ.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("none@univ.com", "Serv1ce$Test"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 비밀번호_불일치_로그인_시_예외() {
        given(userRepository.findByEmail("test@univ.com")).willReturn(Optional.of(sampleUser));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login("test@univ.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 정상_토큰_갱신() {
        UUID userId = UUID.randomUUID();
        given(authStore.findUserIdByRefreshToken("old-refresh")).willReturn(Optional.of(userId));
        given(userRepository.findById(userId)).willReturn(Optional.of(sampleUser));
        given(jwtProvider.generateAccessToken(userId, UserRole.USER)).willReturn("new-access");
        given(jwtProvider.getAccessTokenExpirySeconds()).willReturn(1800L);

        TokenRefreshResponse response = authService.refresh("old-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access");
    }

    @Test
    void 유효하지_않은_리프레시_토큰_시_예외() {
        given(authStore.findUserIdByRefreshToken("bad-token")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
