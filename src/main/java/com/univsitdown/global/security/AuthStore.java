package com.univsitdown.global.security;

import java.util.Optional;
import java.util.UUID;

public interface AuthStore {
    void saveRefreshToken(UUID userId, String token);
    Optional<UUID> findUserIdByRefreshToken(String token);
    void deleteRefreshTokenByUserId(UUID userId);
}
