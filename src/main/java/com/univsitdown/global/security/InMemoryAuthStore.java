package com.univsitdown.global.security;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    // 이메일 → 발급된 6자리 인증 코드 (운영에서는 Redis TTL 180s)
    private final Map<String, String> emailCodes = new ConcurrentHashMap<>();

    // 최근 1분 이내에 코드를 발송한 이메일 목록 (운영에서는 Redis TTL 60s)
    // ConcurrentHashMap.newKeySet(): ConcurrentHashMap 기반의 thread-safe Set
    private final Set<String> rateLimitedEmails = ConcurrentHashMap.newKeySet();

    // 인증 코드 확인을 완료한 이메일 목록 (운영에서는 Redis TTL 600s)
    private final Set<String> verifiedEmails = ConcurrentHashMap.newKeySet();

    // userId → refreshToken (로그인 사용자 기준 역방향 조회용)
    private final Map<UUID, String> userToToken = new ConcurrentHashMap<>();

    // refreshToken → userId (토큰 갱신 시 userId 역조회용)
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
        // 운영에서는 Redis TTL로 60초 후 자동 만료되지만, 여기서는 수동 제거 없이 재시작 시 초기화
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
        // 회원가입 완료 후 인증 상태를 제거해 재사용을 막는다
        verifiedEmails.remove(email);
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
