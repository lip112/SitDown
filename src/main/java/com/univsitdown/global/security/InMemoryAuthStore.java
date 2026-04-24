package com.univsitdown.global.security;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class InMemoryAuthStore implements AuthStore {

    private final Map<String, String> emailCodes = new ConcurrentHashMap<>();
    private final Set<String> rateLimitedEmails = ConcurrentHashMap.newKeySet();
    private final Set<String> verifiedEmails = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> userToToken = new ConcurrentHashMap<>();
    private final Map<String, UUID> tokenToUser = new ConcurrentHashMap<>();

    public InMemoryAuthStore() {
        log.warn("[AuthStore] InMemoryAuthStore 사용 중 — TTL 미적용, 개발 환경 전용");
    }

    @Override
    public void saveEmailCode(String email, String code) {
        emailCodes.put(email, code);
    }

    @Override
    public Optional<String> findEmailCode(String email) {
        return Optional.ofNullable(emailCodes.get(email));
    }

    @Override
    public void deleteEmailCode(String email) {
        emailCodes.remove(email);
    }

    @Override
    public boolean isEmailRateLimited(String email) {
        return rateLimitedEmails.contains(email);
    }

    @Override
    public void markEmailSent(String email) {
        rateLimitedEmails.add(email);
    }

    @Override
    public void markEmailVerified(String email) {
        verifiedEmails.add(email);
    }

    @Override
    public boolean isEmailVerified(String email) {
        return verifiedEmails.contains(email);
    }

    @Override
    public void deleteEmailVerified(String email) {
        verifiedEmails.remove(email);
    }

    @Override
    public void saveRefreshToken(UUID userId, String token) {
        String oldToken = userToToken.put(userId, token);
        if (oldToken != null) {
            tokenToUser.remove(oldToken);
        }
        tokenToUser.put(token, userId);
    }

    @Override
    public Optional<UUID> findUserIdByRefreshToken(String token) {
        return Optional.ofNullable(tokenToUser.get(token));
    }

    @Override
    public void deleteRefreshTokenByUserId(UUID userId) {
        String token = userToToken.remove(userId);
        if (token != null) {
            tokenToUser.remove(token);
        }
    }
}
