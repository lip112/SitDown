# UNIV SITDOWN - Backend

> Claude Code가 이 프로젝트에서 작업할 때 반드시 따라야 하는 규칙 문서.
> 모든 세션 시작 시 자동으로 읽히며, 이 규칙이 모든 기본값보다 우선한다.

## 프로젝트 개요

대학교 내 좌석 예약 시스템 **UNIV SITDOWN**의 백엔드 서버.
Flutter 클라이언트(별도 리포)와 REST API로 통신한다.

핵심 도메인은 **좌석 예약의 동시성 제어**이며, 이 프로젝트의 기술적 가치도 여기에 집중되어 있다.

---

## 기술 스택

| 영역 | 선택 | 비고 |
|---|---|---|
| 언어 | Java 17 | Record, Sealed Class 적극 활용 |
| 프레임워크 | Spring Boot 3.5.x | Spring Web, Data JPA, Security, Validation |
| 빌드 | Gradle (Kotlin DSL) | `build.gradle.kts` |
| DB | PostgreSQL 16 | btree_gist 확장 사용 (EXCLUDE 제약용) |
| 캐시/락 | Redis 7.2 | Lettuce 드라이버, Redisson(분산 락) |
| 마이그레이션 | Flyway | `V{버전}__{설명}.sql` |
| 인증 | JWT (jjwt 0.12.x) | Access + Refresh |
| 문서 | springdoc-openapi | `/swagger-ui.html` |
| 테스트 | JUnit 5 + Testcontainers | PostgreSQL/Redis 컨테이너 |
| 배포 | AWS ECS Fargate | GitHub Actions CI/CD |

---

## 핵심 문서 (작업 전 반드시 참조)

- **@docs/01-spec.md** — 기능 명세서 (화면 ID, 기능 ID, 비즈니스 규칙, 데이터 모델)
- **@docs/02-api.md** — API 명세서 (엔드포인트, 에러 코드, 동시성 정책) ← **API 구현 시 필수**
- **@docs/03-backend-roadmap.md** — Phase별 학습 로드맵

**중요**: API를 구현할 때는 반드시 `@docs/02-api.md`의 해당 API 섹션을 먼저 확인하고, Request/Response/Error 모두를 명세대로 맞춘다. 명세와 다르게 구현하려면 먼저 사용자에게 질문할 것.

---

## 패키지 구조 (도메인 기반)

```
com.univsitdown
├── UnivSitdownApplication.java
├── global/                        ← 공통 유틸, 설정, 예외
│   ├── config/                    (SecurityConfig, RedisConfig, JpaConfig)
│   ├── exception/                 (BusinessException, ErrorCode, GlobalExceptionHandler)
│   ├── response/                  (ApiResponse, PageResponse, ErrorResponse)
│   └── security/                  (JwtProvider, JwtFilter, CurrentUser)
├── auth/                          ← AUTH-XX
│   ├── controller/
│   ├── service/
│   ├── dto/
│   └── domain/
├── user/                          ← USER-XX
├── space/                         ← SPACE-XX, SEAT-XX
├── reservation/                   ← RSV-XX (핵심 도메인)
├── stat/                          ← STAT-XX
├── notice/                        ← NOTI-XX
└── admin/                         ← ADMIN-XX
```

각 도메인 패키지는 `controller`, `service`, `repository`, `domain`, `dto` 하위 패키지를 가진다.

---

## 코딩 컨벤션

### 네이밍
- **약어 금지**: `Rsv` ❌ → `Reservation` ✅, `Usr` ❌ → `User` ✅
- **DTO 접미사**: Request는 `XxxRequest`, Response는 `XxxResponse` (예: `CreateReservationRequest`)
- **예외 접미사**: `XxxException` (예: `SeatAlreadyReservedException`)
- **Enum value**: `SCREAMING_SNAKE_CASE` (예: `READING_ROOM`, `IN_USE`)

### 클래스 설계
- Request/Response DTO는 **Java Record** 사용
- Entity는 **Lombok `@Getter`만**, Setter 금지 (변경은 메서드로)
- `@Builder` 남용 금지 — 생성 방법이 1~2가지면 정적 팩토리 메서드(`of`, `from`) 선호
- Controller는 **얇게**, 로직은 Service로

### 예외 처리
- 모든 비즈니스 예외는 `BusinessException`을 상속
- `RuntimeException`을 직접 던지지 말 것
- 에러 코드는 `ErrorCode` enum에 등록되어야 함 (`@docs/02-api.md` 8장 참조)

```java
// ✅ 올바른 예
throw new SeatAlreadyReservedException();
// 내부적으로 ErrorCode.SEAT_ALREADY_RESERVED 사용

// ❌ 금지
throw new RuntimeException("이미 예약됨");
throw new IllegalStateException("...");  // 비즈니스 로직에서 금지
```

### 트랜잭션
- Service 메서드에 `@Transactional` 명시
- 읽기 전용은 `@Transactional(readOnly = true)`
- 예약 생성·연장·취소는 반드시 `@Transactional` 필수
- 트랜잭션 안에서 외부 API 호출 금지 (커밋 지연)

### 로깅
- `System.out.println` **절대 금지**
- SLF4J 사용: `private static final Logger log = LoggerFactory.getLogger(Xxx.class);`
- 또는 Lombok `@Slf4j`
- 민감 정보(비밀번호, 토큰) 절대 로그에 남기지 말 것
- 에러 로그에는 `traceId`가 자동으로 MDC에 포함됨

---

## API 응답 포맷 (절대 규칙)

### 성공 (단일 리소스)
```json
{ "id": "...", "name": "..." }
```

### 성공 (목록)
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8,
  "hasNext": true
}
```

### 실패 (모든 에러)
```json
{
  "code": "RSV-004",
  "message": "이미 해당 시간대에 예약된 좌석입니다.",
  "timestamp": "2026-04-22T09:00:00Z",
  "traceId": "abc-123",
  "path": "/api/reservations"
}
```

자세한 포맷은 `@docs/02-api.md` 2장 참조.

---

## 에러 코드 규칙

**형식**: `{DOMAIN}-{3자리숫자}` (예: `AUTH-201`, `RSV-004`, `SEAT-001`)

### 번호 대역
- `1xx` — 입력 검증 실패
- `2xx` — 자격 증명/권한 문제
- `0xx` — 리소스 없음 / 기타

### 새 에러 코드 추가 시 반드시
1. `ErrorCode` enum에 추가
2. `@docs/02-api.md` 8장에 추가
3. 해당 API 섹션의 Error Responses 표에도 추가

기존 에러 코드 목록은 `@docs/02-api.md` 8장 참조.

---

## 테스트 규칙

### Service 레이어
- 단위 테스트 **필수**, `@MockBean` 또는 Mockito 사용
- 정상 케이스 1개 + 에러 케이스 **최소 2개**
- 예약 관련 Service는 동시성 통합 테스트 추가

### Controller 레이어
- `@WebMvcTest` 사용, `MockMvc`로 요청/응답 검증
- Validation 에러 케이스 포함

### Repository 레이어
- `@DataJpaTest` + Testcontainers (실제 PostgreSQL)
- 쿼리 메서드 커스텀 시에만 테스트

### 동시성 테스트 (Phase 4 이후 필수)
- `ExecutorService`로 N개 동시 요청
- `CountDownLatch`로 동기화
- 성공 1개 + 정당한 실패 N-1개 확인

```java
@Test
void 동시에_100명이_같은_좌석을_예약하면_1명만_성공한다() throws Exception {
    int threadCount = 100;
    ExecutorService executor = Executors.newFixedThreadPool(32);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger success = new AtomicInteger();
    AtomicInteger conflict = new AtomicInteger();

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                reservationService.reserve(command);
                success.incrementAndGet();
            } catch (SeatAlreadyReservedException e) {
                conflict.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }
    latch.await();

    assertThat(success.get()).isEqualTo(1);
    assertThat(conflict.get()).isEqualTo(99);
}
```

---

## 동시성 처리 규칙 (프로젝트 핵심)

예약 생성·연장은 **이중 방어선**으로 처리한다:

1. **애플리케이션 레벨**: `SELECT ... FOR UPDATE` (비관적 락) + `@Transactional`
2. **DB 레벨**: `reservation` 테이블에 `(seat_id, tsrange)` EXCLUDE 제약
3. **(필요 시)** Redisson 분산 락 추가

자세한 SQL과 구현 예시는 `@docs/02-api.md` 6장 참조.

**중요**: 예약 생성 코드를 작성할 때는 반드시 동시성 테스트를 함께 작성한다. 테스트 없는 예약 로직은 머지 금지.

---

## DB 마이그레이션 규칙

- **Flyway 필수**, JPA `ddl-auto=update` 절대 금지 (prod에서는 `validate`)
- 마이그레이션 파일: `src/main/resources/db/migration/V{번호}__{설명}.sql`
  - 예: `V1__create_user_table.sql`, `V2__create_space_and_seat.sql`
- 버전 번호는 순차 증가, **기존 파일 수정 금지** (새 버전으로 추가)
- 로컬에서 먼저 실행 후 커밋

---

## Redis 사용 규칙

### 키 네이밍 컨벤션
```
{도메인}:{용도}:{식별자}
예:
  auth:refresh:{userId}
  auth:email_verify:{email}
  space:congestion:{spaceId}
  reservation:lock:{seatId}
```

### TTL 가이드
- 공간 목록/혼잡도: 30초
- 좌석 배치 상태: 10초 또는 캐시 생략
- 이메일 인증 코드: 180초
- Refresh Token: 14일

### 무효화 타이밍
- 예약 생성/취소/연장 시 해당 공간의 관련 캐시 `@CacheEvict`
- 관리자가 좌석 상태 변경 시 공간 캐시 무효화

---

## 현재 진행 단계

> 이 섹션은 Phase 진행할 때마다 업데이트한다.

**현재 Phase**: Phase 1 — 기반 다지기 (진행 예정)
**다음 Phase**: Phase 2 — 도메인 구현
**완료된 Phase**: 없음

---

## 금지 사항 (Hard Rules)

다음은 **어떤 이유로도** 해선 안 되는 것들이다. Claude Code가 유혹되더라도 거부한다.

1. **명세에 없는 API를 임의로 추가하지 말 것**
   → 필요하면 먼저 사용자에게 "명세 업데이트가 필요하다"고 알리고 승인받기.

2. **에러 코드를 임의로 만들지 말 것**
   → `@docs/02-api.md` 8장 목록에 있는 코드만 사용. 새 코드가 필요하면 사용자에게 확인.

3. **`BusinessException` 외의 `RuntimeException`을 던지지 말 것**
   (단, `IllegalArgumentException`은 null 방어 등 내부 용도로만)

4. **`System.out.println` 사용 금지**
   → SLF4J 로거만 사용.

5. **JPA `ddl-auto=update`/`create` 사용 금지**
   → 로컬이라도 Flyway만 사용. 스키마 변경은 마이그레이션 파일로.

6. **트랜잭션 내 외부 API 호출 금지**
   → 이메일 발송, 푸시 알림 등은 이벤트 후처리 또는 트랜잭션 외부에서.

7. **비밀번호·토큰·개인정보를 로그로 남기지 말 것**

8. **테스트 없이 예약 관련 로직을 병합하지 말 것**

---

## 작업 시 권장 플로우

### 새 API 구현 요청이 들어왔을 때
1. `@docs/02-api.md`에서 해당 API ID를 찾아 스펙 확인
2. Request/Response DTO를 Record로 정의
3. Entity가 필요하면 마이그레이션(`.sql`)과 함께 생성
4. Service 구현 — 명세의 모든 ErrorCase를 커버
5. Controller 작성 — 경로/메서드/인증 요구사항 반영
6. 테스트 작성 (정상 + 에러 케이스 최소 3개)
7. 사용자에게 요약 보고

### 기존 코드 수정 요청이 들어왔을 때
1. 영향 범위 먼저 파악 (어떤 API가 영향 받는지)
2. 테스트가 있다면 먼저 실행해서 통과 여부 확인
3. 수정 후 관련 테스트 재실행
4. 명세 변경이 필요하면 사용자에게 확인

### 막힐 때
- 명세가 애매하면 **추측하지 말고 사용자에게 질문**할 것
- "대충 이럴 것 같다"로 진행하면 나중에 다 갈아엎게 된다

---

## 세션 관리 팁

- 긴 작업 후에는 `/clear` 권장
- Phase 하나 끝날 때마다 커밋 + `git tag phase-N-done`
- 복잡한 설계 작업은 Opus, 반복 CRUD는 Sonnet으로 전환하여 Pro 한도 관리

---

## 참고 링크

- Spring Boot 공식 문서: https://docs.spring.io/spring-boot/docs/3.5.x/reference/html/
- Spring Data JPA: https://docs.spring.io/spring-data/jpa/reference/
- PostgreSQL 16: https://www.postgresql.org/docs/16/
- Redisson: https://github.com/redisson/redisson/wiki
