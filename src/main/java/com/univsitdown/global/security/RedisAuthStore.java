package com.univsitdown.global.security;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh Token을 양방향 조회할 수 있도록 두 가지 키를 동시에 저장한다.
 *   - auth:refresh:user:{userId}  → token  (userId로 토큰 조회, 로그아웃 시 사용)
 *   - auth:refresh:token:{token}  → userId (토큰으로 userId 조회, 갱신 시 사용)
 * 두 키는 항상 같은 TTL(14일)로 함께 생성·삭제해 불일치를 방지한다.
 */
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
        // 인증 완료 마크는 10분간 유지 — 회원가입 완료까지 허용하는 여유 시간
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
        // user→token 역방향 키로 실제 토큰 값을 먼저 가져와 token→user 키도 함께 삭제
        String token = redis.opsForValue().get(REFRESH_USER_PREFIX + userId);
        if (token != null) {
            redis.delete(REFRESH_TOKEN_PREFIX + token);
        }
        redis.delete(REFRESH_USER_PREFIX + userId);
    }
}
