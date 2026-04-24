# Phase 3 인증/인가 설계

> 작성일: 2026-04-24 | 대상: UNIV SITDOWN 백엔드

## 1. 개요

Spring Security + JWT 기반 인증/인가 구현. Phase 2에서 임시로 `X-User-Id` 헤더를 사용하던 방식을 `@AuthenticationPrincipal`로 교체하고, 모든 API에 JWT 보호를 적용한다.

**구현 범위**
- AUTH-01 ~ AUTH-07 엔드포인트 구현
- JWT 발급/검증 인프라 (JwtProvider, JwtFilter)
- Spring Security 필터 체인 완성
- UserController, AdminSpaceController 인증 교체

## 2. 접근법

**JWT 클레임 기반 경량 방식** 채택.

매 요청마다 DB 조회하는 `UserDetailsService` 방식 대신, JWT 토큰 파싱 결과(userId, role)만으로 `Authentication` 객체를 생성한다. 토큰 TTL이 30분으로 짧아 사용자 정보 불일치 위험이 허용 범위 내에 있다.

## 3. 패키지 구조

```
com/univsitdown/
├── auth/
│   ├── controller/   AuthController.java
│   ├── service/      AuthService.java
│   ├── dto/          SignupRequest, SignupResponse
│   │                 LoginRequest, LoginResponse
│   │                 EmailSendRequest, EmailSendResponse
│   │                 EmailVerifyRequest, EmailVerifyResponse
│   │                 TokenRefreshRequest, TokenRefreshResponse
│   │                 PasswordResetRequest
│   └── exception/    EmailDuplicatedException
│                     InvalidCredentialsException
│                     EmailNotVerifiedException
│                     InvalidEmailCodeException
│                     ExpiredEmailCodeException
│                     AccountLockedException
│                     ExpiredRefreshTokenException
│                     InvalidRefreshTokenException
│
└── global/
    ├── security/     JwtProvider.java
    │                 JwtFilter.java
    │                 CurrentUser.java        ← @AuthenticationPrincipal 래퍼
    │                 UserPrincipal.java      ← record(UUID userId, UserRole role)
    │                 MailService.java        ← interface
    │                 LogMailService.java     ← 로그 출력 구현체
    └── config/       SecurityConfig.java (수정)
                      RedisConfig.java (수정 — fallback 포함)
```

## 4. 토큰 설계

| 항목 | 값 |
|---|---|
| 알고리즘 | HS256 |
| Access Token TTL | 30분 |
| Refresh Token | UUID 랜덤 문자열, TTL 14일 |
| Access Token claim | `sub`(userId), `role`, `exp` |

## 5. 요청 흐름

```
클라이언트 요청
  → JwtFilter (OncePerRequestFilter)
      Authorization 헤더에서 "Bearer {token}" 추출
      → JwtProvider.parse() → UserPrincipal(userId, role)
      → SecurityContextHolder에 Authentication 설정
  → Controller
      → @CurrentUser UserPrincipal principal 주입
```

## 6. Refresh Token 저장 (Redis + Fallback)

```
RefreshTokenStore (interface)
  ├── RedisRefreshTokenStore   — "auth:refresh:{userId}" 키, TTL 14일
  └── InMemoryRefreshTokenStore — ConcurrentHashMap (Redis 연결 실패 시)
```

애플리케이션 시작 시 `RedisTemplate.opsForValue().get("ping")` 호출로 연결을 확인하고, `RedisConnectionFailureException` 발생 시 InMemory 구현체를 사용한다. `@ConditionalOnBean` / `@Primary` 조합으로 구현체 전환이 자동으로 이루어진다.

## 7. 이메일 인증 흐름

```
AUTH-02 발송:
  Redis: "auth:email_verify:{email}" = {6자리 코드}, TTL 180초
  MailService.send() → LogMailService → log.info("[MAIL] to={} code={}")

AUTH-03 확인:
  Redis에서 코드 조회 → 일치 시 "auth:email_verified:{email}" = true, TTL 10분
  불일치 → AUTH-111 / 키 없음 → AUTH-112

AUTH-01 회원가입:
  "auth:email_verified:{email}" 존재 확인 → 없으면 AUTH-103
```

## 8. 경로별 권한

| 경로 | 권한 |
|---|---|
| `/api/auth/**` | permitAll |
| `/api/health`, `/swagger-ui/**`, `/api-docs/**` | permitAll |
| `/api/admin/**` | ADMIN role |
| 그 외 | authenticated (USER, ADMIN) |

## 9. 기존 코드 변경

### UserController
```java
// Before: @RequestHeader("X-User-Id") UUID userId
// After:  @CurrentUser UserPrincipal principal
```

### AdminSpaceController
- `hasRole('ADMIN')` SecurityConfig에서 경로 수준으로 적용 (컨트롤러 어노테이션 불필요)

### SecurityConfig
- JwtFilter를 `UsernamePasswordAuthenticationFilter` 앞에 등록
- permitAll / ADMIN / authenticated 경로 분리

## 10. 테스트 전략

| 레이어 | 방법 | 주요 케이스 |
|---|---|---|
| JwtProvider | 순수 단위 | 토큰 생성/파싱, 만료 토큰 거부 |
| AuthService | Mockito 단위 | 정상 회원가입, 이메일 중복, 미인증, 정상 로그인, 자격증명 실패, 토큰 갱신 |
| AuthController | @WebMvcTest + MockMvc | 요청 검증 400, 정상 응답 201/200 |
| JwtFilter | MockMvc with Security | 토큰 없는 요청 401, 유효 토큰 통과, 만료 토큰 401 |

## 11. 계정 잠금 및 AUTH-07 범위

**계정 잠금 (AUTH-202)**: 이번 Phase에서는 구현하지 않는다. 로그인 실패 카운터를 Redis에 관리하는 로직이 필요하며, 이는 별도 작업으로 Phase 6(운영 준비) 시 추가한다. `AccountLockedException`은 exception 클래스만 정의해 둔다.

**AUTH-07 비밀번호 재설정**: 엔드포인트만 구현하고, `MailService.sendPasswordReset()` 호출 시 `LogMailService`가 `log.info("[MAIL] 비밀번호 재설정 링크: ...")`로 출력한다. 실제 링크 생성 로직(토큰 발급, DB 저장)은 이메일 SMTP 구현 시 함께 완성한다.

## 12. DB 마이그레이션

없음. User 테이블은 이미 존재하며, Refresh Token은 Redis(또는 메모리)에 저장한다.
