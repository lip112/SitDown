package com.univsitdown.global.security;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로컬 개발 전용 AuthStore 구현체.
 * Redis 없이 JVM 메모리에 인증 상태를 저장한다.
 *
 * 주의: TTL이 없어 서버 재시작 전까지 만료되지 않으며,
 * 서버가 여러 대일 경우 인스턴스 간 상태가 공유되지 않는다.
 * 운영 환경에서는 RedisAuthStore를 사용한다.
 */
@Slf4j
public class InMemoryAuthStore implements AuthStore {

    // userId → refreshToken (로그인 사용자 기준 역방향 조회용)
    private final Map<UUID, String> userToToken = new ConcurrentHashMap<>();

    // refreshToken → userId (토큰 갱신 시 userId 역조회용)
    private final Map<String, UUID> tokenToUser = new ConcurrentHashMap<>();

    public InMemoryAuthStore() {
        log.warn("[AuthStore] InMemoryAuthStore 사용 중 — TTL 미적용, 개발 환경 전용");
    }

    @Override
    public void saveRefreshToken(UUID userId, String token) {
        // 재로그인 시 기존 토큰을 tokenToUser에서 먼저 제거해 고아 엔트리를 방지한다
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
        // userToToken에서 토큰 값을 꺼내 tokenToUser도 함께 제거 (양방향 정리)
        String token = userToToken.remove(userId);
        if (token != null) {
            tokenToUser.remove(token);
        }
    }
}
