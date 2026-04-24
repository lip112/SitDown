package com.univsitdown.global.security;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public class RedisAuthStore implements AuthStore {

    private static final String EMAIL_CODE_PREFIX = "auth:email_verify:";
    private static final String EMAIL_RATE_PREFIX = "auth:email_ratelimit:";
    private static final String EMAIL_VERIFIED_PREFIX = "auth:email_verified:";
    private static final String REFRESH_USER_PREFIX = "auth:refresh:user:";
    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:token:";

    private final StringRedisTemplate redis;

    public RedisAuthStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void saveEmailCode(String email, String code) {
        redis.opsForValue().set(EMAIL_CODE_PREFIX + email, code, Duration.ofSeconds(180));
    }

    @Override
    public Optional<String> findEmailCode(String email) {
        return Optional.ofNullable(redis.opsForValue().get(EMAIL_CODE_PREFIX + email));
    }

    @Override
    public void deleteEmailCode(String email) {
        redis.delete(EMAIL_CODE_PREFIX + email);
    }

    @Override
    public boolean isEmailRateLimited(String email) {
        return Boolean.TRUE.equals(redis.hasKey(EMAIL_RATE_PREFIX + email));
    }

    @Override
    public void markEmailSent(String email) {
        redis.opsForValue().set(EMAIL_RATE_PREFIX + email, "1", Duration.ofSeconds(60));
    }

    @Override
    public void markEmailVerified(String email) {
        redis.opsForValue().set(EMAIL_VERIFIED_PREFIX + email, "true", Duration.ofSeconds(600));
    }

    @Override
    public boolean isEmailVerified(String email) {
        return Boolean.TRUE.equals(redis.hasKey(EMAIL_VERIFIED_PREFIX + email));
    }

    @Override
    public void deleteEmailVerified(String email) {
        redis.delete(EMAIL_VERIFIED_PREFIX + email);
    }

    @Override
    public void saveRefreshToken(UUID userId, String token) {
        Duration ttl = Duration.ofDays(14);
        redis.opsForValue().set(REFRESH_USER_PREFIX + userId, token, ttl);
        redis.opsForValue().set(REFRESH_TOKEN_PREFIX + token, userId.toString(), ttl);
    }

    @Override
    public Optional<UUID> findUserIdByRefreshToken(String token) {
        String userId = redis.opsForValue().get(REFRESH_TOKEN_PREFIX + token);
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    @Override
    public void deleteRefreshTokenByUserId(UUID userId) {
        String token = redis.opsForValue().get(REFRESH_USER_PREFIX + userId);
        if (token != null) {
            redis.delete(REFRESH_TOKEN_PREFIX + token);
        }
        redis.delete(REFRESH_USER_PREFIX + userId);
    }
}
