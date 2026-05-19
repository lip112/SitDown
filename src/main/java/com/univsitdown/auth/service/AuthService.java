package com.univsitdown.auth.service;

import com.univsitdown.auth.dto.*;
import com.univsitdown.auth.exception.*;
import com.univsitdown.global.security.AuthStore;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.user.domain.User;
import com.univsitdown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthStore authStore;

    /**
     * 이메일 중복 확인 → 저장 순서를 지킨다.
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
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
        return SignupResponse.from(user);
    }

    @Transactional(readOnly = true)
    public EmailCheckResponse checkEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailDuplicatedException();
        }
        return new EmailCheckResponse(email, true);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        // 이메일 존재 여부와 비밀번호 불일치를 동일한 예외로 처리 — 계정 열거 공격 방지
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getRole());
        // Refresh Token은 JWT가 아닌 랜덤 UUID — 만료·무효화를 Redis가 전담
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

}
