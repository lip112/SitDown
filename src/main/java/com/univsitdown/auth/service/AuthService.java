package com.univsitdown.auth.service;

import com.univsitdown.auth.dto.*;
import com.univsitdown.auth.exception.*;
import com.univsitdown.global.security.AuthStore;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.global.security.MailService;
import com.univsitdown.user.domain.User;
import com.univsitdown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthStore authStore;
    private final MailService mailService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!authStore.isEmailVerified(request.email())) {
            throw new EmailNotVerifiedException();
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailDuplicatedException();
        }
        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.phone(),
                request.affiliation()
        );
        userRepository.save(user);
        authStore.deleteEmailVerified(request.email());
        return SignupResponse.from(user);
    }

    public EmailSendResponse sendEmailCode(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailDuplicatedException();
        }
        if (authStore.isEmailRateLimited(email)) {
            throw new EmailSendRateLimitException();
        }
        String code = generateSixDigitCode();
        authStore.saveEmailCode(email, code);
        authStore.markEmailSent(email);
        mailService.sendVerificationCode(email, code);
        return new EmailSendResponse(email, Instant.now().plusSeconds(180).toString());
    }

    public EmailVerifyResponse verifyEmailCode(String email, String code) {
        String stored = authStore.findEmailCode(email)
                .orElseThrow(ExpiredEmailCodeException::new);
        if (!stored.equals(code)) {
            throw new InvalidEmailCodeException();
        }
        authStore.markEmailVerified(email);
        authStore.deleteEmailCode(email);
        return new EmailVerifyResponse(true);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = UUID.randomUUID().toString();
        authStore.saveRefreshToken(user.getId(), refreshToken);
        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtProvider.getAccessTokenExpirySeconds(),
                LoginResponse.LoginUserInfo.from(user)
        );
    }

    @Transactional(readOnly = true)
    public TokenRefreshResponse refresh(String refreshToken) {
        UUID userId = authStore.findUserIdByRefreshToken(refreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidRefreshTokenException::new);
        String newAccessToken = jwtProvider.generateAccessToken(userId, user.getRole());
        return new TokenRefreshResponse(newAccessToken, jwtProvider.getAccessTokenExpirySeconds());
    }

    public void logout(UUID userId) {
        authStore.deleteRefreshTokenByUserId(userId);
    }

    public void resetPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user ->
                mailService.sendPasswordReset(email, "https://univ-sitdown.com/reset?token=placeholder")
        );
    }

    private String generateSixDigitCode() {
        return String.format("%06d", new Random().nextInt(1_000_000));
    }
}
