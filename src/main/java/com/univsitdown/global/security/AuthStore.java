package com.univsitdown.global.security;

import java.util.Optional;
import java.util.UUID;

public interface AuthStore {
    void saveEmailCode(String email, String code);
    Optional<String> findEmailCode(String email);
    void deleteEmailCode(String email);

    boolean isEmailRateLimited(String email);
    void markEmailSent(String email);

    void markEmailVerified(String email);
    boolean isEmailVerified(String email);
    void deleteEmailVerified(String email);

    void saveRefreshToken(UUID userId, String token);
    Optional<UUID> findUserIdByRefreshToken(String token);
    void deleteRefreshTokenByUserId(UUID userId);
}
