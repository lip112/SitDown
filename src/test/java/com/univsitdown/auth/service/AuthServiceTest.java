package com.univsitdown.auth.service;

import com.univsitdown.auth.dto.*;
import com.univsitdown.auth.exception.*;
import com.univsitdown.global.security.AuthStore;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.global.security.MailService;
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
    @Mock MailService mailService;
    @InjectMocks AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.create("test@univ.com", "hashed", "홍길동", "010-1234-5678", "학생");
    }

    @Test
    void 정상_회원가입() {
        given(authStore.isEmailVerified("test@univ.com")).willReturn(true);
        given(userRepository.existsByEmail("test@univ.com")).willReturn(false);
        given(passwordEncoder.encode("P@ssw0rd!")).willReturn("hashed");
        given(userRepository.save(any(User.class))).willReturn(sampleUser);

        SignupRequest request = new SignupRequest("test@univ.com", "P@ssw0rd!", "홍길동", null, null);
        SignupResponse response = authService.signup(request);

        assertThat(response.email()).isEqualTo("test@univ.com");
        assertThat(response.name()).isEqualTo("홍길동");
        then(authStore).should().deleteEmailVerified("test@univ.com");
    }

    @Test
    void 이메일_미인증_회원가입_시_예외() {
        given(authStore.isEmailVerified("test@univ.com")).willReturn(false);

        SignupRequest request = new SignupRequest("test@univ.com", "P@ssw0rd!", "홍길동", null, null);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void 이메일_중복_회원가입_시_예외() {
        given(authStore.isEmailVerified("test@univ.com")).willReturn(true);
        given(userRepository.existsByEmail("test@univ.com")).willReturn(true);

        SignupRequest request = new SignupRequest("test@univ.com", "P@ssw0rd!", "홍길동", null, null);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(EmailDuplicatedException.class);
    }

    @Test
    void 정상_이메일_코드_발송() {
        given(userRepository.existsByEmail("new@univ.com")).willReturn(false);
        given(authStore.isEmailRateLimited("new@univ.com")).willReturn(false);

        EmailSendResponse response = authService.sendEmailCode("new@univ.com");

        assertThat(response.email()).isEqualTo("new@univ.com");
        then(authStore).should().saveEmailCode(eq("new@univ.com"), anyString());
        then(authStore).should().markEmailSent("new@univ.com");
        then(mailService).should().sendVerificationCode(eq("new@univ.com"), anyString());
    }

    @Test
    void 이미_가입된_이메일_코드_발송_시_예외() {
        given(userRepository.existsByEmail("test@univ.com")).willReturn(true);

        assertThatThrownBy(() -> authService.sendEmailCode("test@univ.com"))
                .isInstanceOf(EmailDuplicatedException.class);
    }

    @Test
    void 발송_rate_limit_초과_시_예외() {
        given(userRepository.existsByEmail("new@univ.com")).willReturn(false);
        given(authStore.isEmailRateLimited("new@univ.com")).willReturn(true);

        assertThatThrownBy(() -> authService.sendEmailCode("new@univ.com"))
                .isInstanceOf(EmailSendRateLimitException.class);
    }

    @Test
    void 정상_이메일_코드_확인() {
        given(authStore.findEmailCode("test@univ.com")).willReturn(Optional.of("123456"));

        EmailVerifyResponse response = authService.verifyEmailCode("test@univ.com", "123456");

        assertThat(response.verified()).isTrue();
        then(authStore).should().markEmailVerified("test@univ.com");
        then(authStore).should().deleteEmailCode("test@univ.com");
    }

    @Test
    void 코드_불일치_시_예외() {
        given(authStore.findEmailCode("test@univ.com")).willReturn(Optional.of("123456"));

        assertThatThrownBy(() -> authService.verifyEmailCode("test@univ.com", "999999"))
                .isInstanceOf(InvalidEmailCodeException.class);
    }

    @Test
    void 코드_만료_시_예외() {
        given(authStore.findEmailCode("test@univ.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmailCode("test@univ.com", "123456"))
                .isInstanceOf(ExpiredEmailCodeException.class);
    }

    @Test
    void 정상_로그인() {
        given(userRepository.findByEmail("test@univ.com")).willReturn(Optional.of(sampleUser));
        given(passwordEncoder.matches("P@ssw0rd!", "hashed")).willReturn(true);
        given(jwtProvider.generateAccessToken(any(), eq(UserRole.USER))).willReturn("access-token");
        given(jwtProvider.getAccessTokenExpirySeconds()).willReturn(1800L);

        LoginResponse response = authService.login("test@univ.com", "P@ssw0rd!");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        then(authStore).should().saveRefreshToken(any(), anyString());
    }

    @Test
    void 존재하지_않는_이메일_로그인_시_예외() {
        given(userRepository.findByEmail("none@univ.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("none@univ.com", "P@ssw0rd!"))
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
