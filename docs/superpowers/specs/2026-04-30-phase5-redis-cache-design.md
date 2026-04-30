# Phase 5 — Redis 캐싱 구현 설계

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Spring Cache 어노테이션 기반으로 공간 목록/상세 및 좌석 배치 조회를 Redis에 캐싱하고, 예약 생성·연장·취소 시 캐시를 무효화한다.

**Architecture:** `@Cacheable`/`@CacheEvict` 선언적 캐싱. `CacheConfig`에서 `RedisCacheManager`(Redis 연결 성공 시) 또는 `ConcurrentMapCacheManager`(연결 실패 시) 를 빈으로 등록해 개발 환경에서도 Redis 없이 정상 동작한다. 기존 `AuthStoreConfig`의 fallback 패턴과 동일하다.

**Tech Stack:** Spring Boot 3.5, Spring Cache abstraction, Spring Data Redis (Lettuce), RedisCacheManager, ConcurrentMapCacheManager

---

## 1. 캐시 정책

| 캐시 이름 | 대상 메서드 | TTL (Redis) | 키 |
|---|---|---|---|
| `space:list` | `SpaceService.getSpaces()` | 30초 | `#category + ':' + #keyword + ':' + #pageable` |
| `space:detail` | `SpaceService.getSpace()` | 30초 | `#id` |
| `seat:layout` | `SeatService.getSeatLayout()` | 10초 | `#spaceId` |

**캐시 무효화(evict) 시점** — 아래 이벤트 발생 시 3개 캐시 전체를 `allEntries=true`로 즉시 삭제:

- `ReservationService.reserve()` 성공 시
- `ReservationService.extend()` 성공 시
- `ReservationService.cancel()` 성공 시
- `SeatService.updateSeatStatus()` 성공 시

TTL이 10~30초로 짧으므로 `allEntries` 전체 삭제는 허용 수준이다.

---

## 2. 혼잡도 및 좌석 수 계산

현재 `SpaceListItemResponse`·`SpaceDetailResponse`의 `totalSeats`, `availableSeats`, `congestion` 필드는 stub(0/"LOW"). Phase 5에서 실제 DB 쿼리로 교체한다.

### 계산 로직

```
totalSeats    = 해당 공간의 isEnabled=true 좌석 수
occupiedSeats = 현재 시각 기준 SCHEDULED/IN_USE 예약이 존재하는 좌석 수
availableSeats = totalSeats - occupiedSeats

congestion:
  occupancyRate = occupiedSeats / totalSeats (totalSeats=0이면 LOW)
  < 0.40  → LOW
  < 0.75  → NORMAL
  >= 0.75 → HIGH
```

### 필요한 신규 쿼리

**SeatRepository**
```java
long countBySpaceIdAndIsEnabledTrue(UUID spaceId);
```

**ReservationRepository**
```java
// spaceId에 속한 좌석 중 현재 점유 중인 좌석 수 (SCHEDULED 또는 IN_USE 시간대)
@Query("""
    SELECT COUNT(DISTINCT r.seat.id)
    FROM Reservation r
    WHERE r.seat.space.id = :spaceId
      AND r.status NOT IN ('CANCELED', 'NO_SHOW')
      AND r.startAt <= :now AND r.endAt > :now
""")
long countOccupiedBySpaceId(@Param("spaceId") UUID spaceId, @Param("now") LocalDateTime now);
```

---

## 3. 컴포넌트 변경 목록

### 신규 파일

| 파일 | 역할 |
|---|---|
| `global/config/CacheConfig.java` | `@EnableCaching` + `RedisCacheManager`(Redis 연결 성공) / `ConcurrentMapCacheManager`(실패) 빈 등록 |

### 수정 파일

| 파일 | 변경 내용 |
|---|---|
| `space/service/SpaceService.java` | `@Cacheable` 추가, `SeatRepository`·`ReservationRepository` 주입, stats 계산 로직 추가 |
| `space/service/SeatService.java` | `getSeatLayout()`에 `@Cacheable`, `updateSeatStatus()`에 `@CacheEvict` 추가 |
| `reservation/service/ReservationService.java` | `reserve()`·`extend()`·`cancel()`에 `@CacheEvict` 추가 |
| `space/repository/SeatRepository.java` | `countBySpaceIdAndIsEnabledTrue()` 추가 |
| `reservation/repository/ReservationRepository.java` | `countOccupiedBySpaceId()` 추가 |
| `space/dto/SpaceListItemResponse.java` | `from(Space, int totalSeats, int availableSeats)` 오버로드 추가 |
| `space/dto/SpaceDetailResponse.java` | 동일 |

---

## 4. CacheConfig 구조

```java
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration SPACE_TTL = Duration.ofSeconds(30);
    private static final Duration SEAT_TTL  = Duration.ofSeconds(10);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        try {
            connectionFactory.getConnection().ping();
            log.info("[Cache] Redis 연결 성공 — RedisCacheManager 사용");

            RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                    .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                            new GenericJackson2JsonRedisSerializer()));

            return RedisCacheManager.builder(connectionFactory)
                    .withCacheConfiguration("space:list",   base.entryTtl(SPACE_TTL))
                    .withCacheConfiguration("space:detail", base.entryTtl(SPACE_TTL))
                    .withCacheConfiguration("seat:layout",  base.entryTtl(SEAT_TTL))
                    .build();
        } catch (Exception e) {
            log.warn("[Cache] Redis 연결 실패 — ConcurrentMapCacheManager 사용 (개발 환경 전용)");
            return new ConcurrentMapCacheManager("space:list", "space:detail", "seat:layout");
        }
    }
}
```

---

## 5. @Cacheable / @CacheEvict 적용 패턴

### SpaceService

```java
@Cacheable(value = "space:list", key = "#category + ':' + #keyword + ':' + #pageable")
public PageResponse<SpaceListItemResponse> getSpaces(...) { ... }

@Cacheable(value = "space:detail", key = "#id")
public SpaceDetailResponse getSpace(UUID id) { ... }
```

### SeatService

```java
@Cacheable(value = "seat:layout", key = "#spaceId")
public SeatLayoutResponse getSeatLayout(UUID spaceId, LocalDateTime at) { ... }

@Caching(evict = {
    @CacheEvict(value = "space:list",   allEntries = true),
    @CacheEvict(value = "space:detail", allEntries = true),
    @CacheEvict(value = "seat:layout",  allEntries = true)
})
public void updateSeatStatus(UUID seatId, boolean isEnabled) { ... }
```

### ReservationService

```java
@Caching(evict = {
    @CacheEvict(value = "space:list",   allEntries = true),
    @CacheEvict(value = "space:detail", allEntries = true),
    @CacheEvict(value = "seat:layout",  allEntries = true)
})
public CreateReservationResponse reserve(...) { ... }

// extend(), cancel() 동일 패턴
```

---

## 6. 테스트 계획

### 단위 테스트

- `SpaceServiceTest` — stats 계산 로직 (`totalSeats`, `availableSeats`, `congestion`) Mockito 단위 테스트

### 통합 테스트 (Testcontainers)

- `CacheIntegrationTest` — `@SpringBootTest` + Testcontainers Redis
  - 캐시 미스: 첫 호출 시 DB 쿼리 발생 확인
  - 캐시 히트: 두 번째 호출 시 DB 쿼리 미발생 확인
  - 캐시 evict: 예약 생성 후 캐시 무효화 확인

---

## 7. 직렬화 주의사항

`RedisCacheManager`에 `GenericJackson2JsonRedisSerializer` 사용. `PageResponse`, `SeatLayoutResponse` 등 모든 캐시 대상 클래스는 기본 생성자 또는 Jackson 역직렬화가 가능해야 한다. Java Record는 Jackson이 자동 처리하므로 별도 설정 불필요.

단, `Pageable` 객체는 직렬화 불가이므로 `@Cacheable` 키에 `#pageable.pageNumber + ':' + #pageable.pageSize`로 풀어서 사용한다.
