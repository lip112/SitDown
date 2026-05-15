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

    private static final String REFRESH_USER_PREFIX = "auth:refresh:user:";
    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:token:";

    private final StringRedisTemplate redis;

    public RedisAuthStore(StringRedisTemplate redis) {
        this.redis = redis;
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
