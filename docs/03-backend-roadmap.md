# 백엔드 개발자 성장 로드맵

> UNIV SITDOWN 프로젝트 기반  |  Java 17 · Spring Boot 3.5 · PostgreSQL 16 · Redis 7.2 · AWS

## 전체 로드맵 개요

| Phase | 주제 | 기간 | 핵심 목표 |
|:-:|---|:-:|---|
| 1 | 기반 다지기 | 1~2주 | 환경 세팅 + 프로젝트 구조 + Hello API |
| 2 | 도메인 구현 | 2~3주 | User / Space / Seat CRUD + JPA 기초 |
| 3 | 인증 / 인가 | 1~2주 | Spring Security + JWT 기반 로그인 |
| **4** | **예약 핵심 로직 ★** | **2~3주** | **동시성, 락, 트랜잭션 (이 프로젝트의 꽃)** |
| 5 | Redis 캐싱 | 1~2주 | 혼잡도 / 좌석 상태 캐싱, 분산 락 |
| 6 | 운영 준비 | 1~2주 | 로깅 / 예외 처리 / 모니터링 |
| 7 | AWS 배포 | 1~2주 | CI/CD + 실제 배포 |

> 직장 병행 기준 총 **10~16주**가 적정. 시간이 부족하면 기간을 늘리되, Phase 순서는 건너뛰지 않는 것을 권장한다.

---

## Phase 1. 기반 다지기

> 🎯 **목표**: API 한 개 띄워서 Postman으로 호출 성공

### 배울 내용
- Spring Boot 프로젝트 구조 (Controller / Service / Repository 레이어)
- Gradle 기본 (build.gradle 구성, 의존성 추가 방식)
- `application.yml` 프로파일 분리 (local / dev / prod)
- Lombok, MapStruct 도입 여부 결정
- Git 브랜치 전략 (git-flow 또는 trunk-based)

### 실습 과제
1. Spring Initializr로 프로젝트 생성 (Java 17, Spring Boot 3.2.x)
2. 로컬 PostgreSQL 설치 또는 Docker로 띄우기
3. `GET /api/health` 엔드포인트 구현 및 DB 연결 확인
4. Postman Collection 관리 시작

> ⚠️ **흔한 실수**: 처음부터 멋진 패키지 구조(헥사고날, 클린 아키텍처 등)를 시도하다가 진도가 안 나감. → **레이어드 아키텍처로 시작**하고, 나중에 리팩터링해도 충분하다.

---

## Phase 2. 도메인 구현

> 🎯 **목표**: 사용자 · 공간 · 좌석을 DB에 CRUD 할 수 있다

### 배울 내용
- JPA / Hibernate 기본 (`@Entity`, `@OneToMany`, `@ManyToOne`)
- 연관관계 매핑 — 지연로딩(LAZY)이 기본이라는 감각
- **N+1 문제** — 실제 쿼리 로그를 보며 터지는 걸 경험
- Spring Data JPA Repository와 쿼리 메서드
- Flyway (DB 마이그레이션) — 처음부터 도입 권장
- DTO ↔ Entity 변환 전략
- Bean Validation (`@Valid`, `@NotNull`, `@Size` 등)

### 실습 과제
1. `Space`(공간) CRUD API 구현
2. `Seat`을 행/열 기반으로 벌크 생성하는 API (관리자 기능 `F-ADMIN-02`)
3. 공간 목록 조회 시 일부러 N+1 만들어보고 → `fetch join`으로 해결
4. Flyway로 초기 스키마 버전 관리 (`V1__init.sql`)

### N+1 문제 재현 예시
```java
// 이렇게 쓰면 공간마다 좌석을 가져오는 쿼리가 N번 더 발생
List<Space> spaces = spaceRepository.findAll();
for (Space s : spaces) {
    s.getSeats().size(); // 매번 추가 쿼리
}

// 해결 — JPQL fetch join
@Query("select s from Space s join fetch s.seats")
List<Space> findAllWithSeats();
```

> 📚 **추천 학습 자료**: 김영한 - 자바 ORM 표준 JPA 프로그래밍 / 인프런 JPA 기본편. 이 학습은 백엔드 자바 개발자에게 피할 수 없는 투자다.

---

## Phase 3. 인증 / 인가

> 🎯 **목표**: 회원가입 → 로그인 → JWT로 보호된 API 호출

### 배울 내용
- Spring Security 필터 체인 구조
- JWT — access token 우선 구현, refresh token은 나중
- BCrypt 비밀번호 해싱
- `@AuthenticationPrincipal`로 현재 사용자 꺼내 쓰기
- CORS 설정 — 플러터 클라이언트와 연동 시 필요
- Role 기반 인가 (`USER`, `ADMIN`)

### 실습 과제
1. `POST /api/auth/signup` — 회원가입
2. `POST /api/auth/login` — JWT 발급
3. JWT 필터 구현 및 `SecurityFilterChain` 등록
4. `/api/admin/**` 경로는 `ADMIN` Role만 접근 가능하게 제한

> ⚠️ **함정**: Spring Security는 처음엔 무조건 헤맨다. 필터 체인이 어떻게 동작하는지 그림을 그려가며 이해하자. 바로 이메일 인증 코드 발송까지 구현하려 하지 말고, **먼저 이메일/비밀번호 로그인만** 되게 한다.

---

## Phase 4. 예약 핵심 로직 ★

> 🎯 **목표**: 동시에 100명이 같은 좌석을 예약해도 1명만 성공한다

**이 Phase가 이 프로젝트의 진짜 가치다.** 좌석 예약이라는 도메인은 동시성/락/트랜잭션 문제를 자연스럽게 마주치게 해주며, 기술 블로그 글감도 이 구간에서 가장 많이 나온다.

### 배울 내용
- `@Transactional` 동작 원리 (propagation, isolation)
- **비관적 락 vs 낙관적 락** — 좌석 예약은 보통 비관적 락이 적합
- `SELECT ... FOR UPDATE` (PostgreSQL row-level lock)
- 데드락과 락 순서
- 시간 겹침 체크 쿼리 (`start < ? AND end > ?`)
- Unique constraint / EXCLUDE 제약으로 DB 레벨 방어선
- 스케줄러(`@Scheduled`)로 `NO_SHOW` 자동 처리

### 실습 과제 (순서대로 진행)
1. 단순 예약 API 먼저 구현 (동시성 무시)
2. **JMeter 또는 k6로 동시 요청 100개 날려서 중복 예약이 실제로 발생하는 것을 확인** ← 이 경험이 핵심
3. 비관적 락 적용 후 다시 테스트하여 차이를 수치로 측정
4. 예약 취소/연장 시 트랜잭션 경계 설계
5. `NO_SHOW` 자동 취소 스케줄러 작성

### 비관적 락 적용 예시
```java
// Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from Seat s where s.id = :id")
Optional<Seat> findByIdForUpdate(@Param("id") UUID id);

// Service
@Transactional
public ReservationResponse reserve(ReserveCommand cmd) {
    Seat seat = seatRepository.findByIdForUpdate(cmd.seatId())
        .orElseThrow(SeatNotFoundException::new);

    // 시간대 겹침 검증
    if (reservationRepository.existsOverlapping(
            cmd.seatId(), cmd.startAt(), cmd.endAt())) {
        throw new SeatAlreadyReservedException();
    }
    Reservation r = Reservation.of(seat, cmd);
    return ReservationResponse.from(reservationRepository.save(r));
}
```

> 📝 **블로그 글감 알림**: 이 Phase를 지나며 "좌석 예약 동시성 문제, DB 락으로 풀기까지의 삽질" 같은 flowkater.io 스타일 포스트를 쓸 수 있다. 단순 결과가 아니라 시행착오와 판단 근거를 기록하자.

---

## Phase 5. Redis 캐싱

> 🎯 **목표**: 좌석 배치도 조회가 DB 거치지 않고 빠르게 응답한다

### 배울 내용
- Spring Data Redis (Lettuce 드라이버)
- 캐시 전략 — Cache-Aside 패턴
- TTL 설정과 캐시 무효화 타이밍
- `@Cacheable`, `@CacheEvict` 어노테이션
- 혼잡도를 Redis에 실시간 집계 (Sorted Set 또는 Hash 자료구조)
- Redisson을 이용한 분산 락 — 비관적 락의 대안

### 실습 과제
1. 공간 목록과 좌석 배치도에 캐싱 적용 (TTL 30초~1분)
2. 예약 생성 시 해당 공간의 캐시를 무효화
3. Redisson으로 분산 락 구현 후 DB 비관적 락과 성능/특성 비교
4. 혼잡도를 Redis Hash로 집계해 실시간 조회

> ⚠️ **주의**: 처음부터 Redis를 남발하지 말자. Phase 4에서 DB만으로 돌려본 뒤, 부하 테스트로 병목을 측정하고 "여기에 캐시가 필요하다"는 판단 근거를 확보한 뒤 도입해야 진짜 경험이 된다.

---

## Phase 6. 운영 준비

> 🎯 **목표**: 장애가 나도 원인 파악이 가능한 상태를 만든다

### 배울 내용
- Logback / SLF4J 설정, 로그 레벨 관리
- MDC로 요청 추적 ID(`traceId`) 심기
- `@RestControllerAdvice`로 전역 예외 처리
- 커스텀 예외 체계 (`BusinessException`, 에러 코드 enum)
- Spring Boot Actuator (health, metrics)
- API 문서화 — springdoc-openapi / Swagger UI

### 실습 과제
1. 공통 에러 응답 포맷 정의 (`code`, `message`, `timestamp`, `traceId`)
2. `@RestControllerAdvice`로 예외별 HTTP 상태 매핑
3. 요청당 고유 `traceId`를 MDC에 심어 로그로 추적
4. Swagger UI에서 모든 API 문서 자동 생성 확인

### 커스텀 예외 예시
```java
public enum ErrorCode {
    SEAT_NOT_FOUND("SEAT-001", "좌석을 찾을 수 없습니다"),
    SEAT_ALREADY_RESERVED("SEAT-002", "이미 예약된 좌석입니다"),
    RESERVATION_OUT_OF_HOURS("RSV-001", "운영 시간 외 예약은 불가합니다");
}

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handle(BusinessException e) {
        return ResponseEntity.status(e.getStatus())
            .body(ErrorResponse.of(e.getCode(), e.getMessage()));
    }
}
```

---

## Phase 7. AWS 배포

> 🎯 **목표**: 도메인 주소로 실제 접속 가능한 서비스를 구축한다

### 배울 내용
- EC2 기본 — 또는 ECS Fargate (컨테이너 기반, 더 미래지향적)
- RDS PostgreSQL — 운영 DB
- ElastiCache Redis — 관리형 Redis
- VPC, Security Group, IAM 기본 개념
- Docker로 앱 컨테이너화
- GitHub Actions로 CI/CD 파이프라인
- ALB + Route53으로 도메인 연결
- CloudWatch Logs로 중앙 집중 로그

### 실습 과제
1. Dockerfile과 docker-compose.yml 작성 후 로컬에서 실행
2. ECR에 이미지 푸시 → ECS Fargate에 배포
3. RDS PostgreSQL / ElastiCache Redis 생성 및 연결
4. GitHub Actions 워크플로: `main` 브랜치 머지 시 자동 배포
5. 도메인 구매 후 Route53 + ALB 연결

> 💰 **1년차 팁 — 비용 주의**: AWS는 처음에 비용 폭탄이 날 가능성이 있다. 프리티어를 반드시 확인하고, 안 쓰는 리소스는 종료할 것. 무엇보다 **Billing Alarm**을 꼭 걸어두자 ($5, $20 임계치 추천).

---

## 우선순위 정리

### 꼭 해야 하는 것 (취업/이직 시 가산점)
- **동시성 제어 (Phase 4)** — 가장 강력한 어필 포인트
- 트랜잭션 동작 원리 이해
- N+1 문제 재현 및 해결 경험
- CI/CD 파이프라인 구축 경험

### 있으면 좋은 것
- Redis 분산 락
- 스케줄러 기반 배치 처리
- APM 도입 (Prometheus + Grafana)

### 나중에 해도 되는 것 (지금은 오버엔지니어링)
- 마이크로서비스 아키텍처
- Kafka 등 메시지 큐
- 헥사고날 / 클린 아키텍처

---

## 블로그 글감 (flowkater.io 스타일)

"기술 스택 소개"가 아니라 "**왜 A 대신 B를 선택했는가**"의 판단 과정이 드러나도록 쓰는 것이 1~3년차 구간에서 가장 눈에 띈다.

- 좌석 예약 동시성 문제, DB 락으로 풀기까지의 삽질 과정
- N+1 문제를 JMeter로 재현하고 해결한 과정
- Redis 분산 락 vs PostgreSQL 비관적 락 — 어떤 기준으로 선택할 것인가
- JWT Refresh Token 전략 결정 과정과 트레이드오프
- 좌석 생성 API를 행/열 구조로 설계한 이유
- 혼잡도 집계를 Redis로 옮긴 이유와 부하 테스트 결과

---

## 마지막 조언

1. **혼자 다 만들지 말되, 직접 타이핑하자.** AI에게 구조 질문은 하되, 에러는 로그를 보고 스스로 해석하는 습관이 중요하다.
2. **테스트 코드는 Phase 4부터 반드시 쓰자.** 동시성은 단위테스트보다 통합테스트에서 더 잘 검증된다.
3. **완벽하게 만들려 하지 말고, 먼저 돌아가는 것을 만든 뒤 리팩터링하자.**
4. **"이거 왜 안 되지"로 2시간 이상 끌지 말자.** 막히면 질문하거나 잠깐 멈추고 문제를 재정의하자.

**이 로드맵은 가이드일 뿐이다.** 자신의 학습 속도에 맞춰 유연하게 조정하되, Phase 순서만 지키면 성장의 방향성은 흐트러지지 않는다.
