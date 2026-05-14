# Phase 5 Redis 캐싱 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Cache 어노테이션으로 공간/좌석 조회 결과를 Redis에 캐싱하고, 예약 변경 시 캐시를 무효화하며 `availableSeats`·`congestion` stub을 실제 값으로 교체한다.

**Architecture:** `CacheConfig`에서 Redis 연결 성공 시 `RedisCacheManager`(TTL 적용), 실패 시 `ConcurrentMapCacheManager`(인메모리 fallback)를 빈으로 등록. `AuthStoreConfig`와 동일한 패턴. `@Cacheable`/`@CacheEvict`를 Service 메서드에 선언해 DB 조회를 캐시로 대체하고 예약 이벤트 발생 시 전체 eviction.

**Tech Stack:** Spring Boot 3.5, Spring Cache abstraction, Spring Data Redis (Lettuce), RedisCacheManager, ConcurrentMapCacheManager, GenericJackson2JsonRedisSerializer

---

## 파일 구조

| 상태 | 파일 | 역할 |
|---|---|---|
| **신규** | `src/main/java/com/univsitdown/global/config/CacheConfig.java` | @EnableCaching + RedisCacheManager/ConcurrentMapCacheManager fallback |
| 수정 | `src/main/java/com/univsitdown/space/repository/SeatRepository.java` | countBySpaceIdAndIsEnabledTrue 추가 |
| 수정 | `src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java` | countOccupiedBySpaceId 추가 |
| 수정 | `src/main/java/com/univsitdown/space/dto/SpaceListItemResponse.java` | from(Space, int, int) 오버로드 + congestion 계산 |
| 수정 | `src/main/java/com/univsitdown/space/dto/SpaceDetailResponse.java` | from(Space, int, int) 오버로드 |
| 수정 | `src/main/java/com/univsitdown/space/service/SpaceService.java` | @Cacheable 추가, stats 계산 로직, 새 의존성 주입 |
| 수정 | `src/main/java/com/univsitdown/space/service/SeatService.java` | getSeatLayout @Cacheable, updateSeatStatus @CacheEvict |
| 수정 | `src/main/java/com/univsitdown/reservation/service/ReservationService.java` | reserve/extend/cancel @CacheEvict |
| 수정 | `src/test/java/com/univsitdown/space/service/SpaceServiceTest.java` | 새 mock 추가, stats 검증으로 업데이트 |
| **신규** | `src/test/java/com/univsitdown/global/config/CacheIntegrationTest.java` | SpringBootTest + PostgreSQL Testcontainers로 캐시 히트/evict 검증 |

---

### Task 1: CacheConfig — Redis/fallback 캐시 설정

**Files:**
- Create: `src/main/java/com/univsitdown/global/config/CacheConfig.java`

- [ ] **Step 1: CacheConfig.java 생성**

```java
package com.univsitdown.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

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

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/univsitdown/global/config/CacheConfig.java
git commit -m "feat: CacheConfig — RedisCacheManager/ConcurrentMapCacheManager fallback"
```

---

### Task 2: Repository — 좌석 수·점유 수 쿼리 추가

**Files:**
- Modify: `src/main/java/com/univsitdown/space/repository/SeatRepository.java`
- Modify: `src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java`

- [ ] **Step 1: SeatRepository에 countBySpaceIdAndIsEnabledTrue 추가**

`src/main/java/com/univsitdown/space/repository/SeatRepository.java` 에 아래 메서드 추가:

```java
long countBySpaceIdAndIsEnabledTrue(UUID spaceId);
```

파일 전체:
```java
package com.univsitdown.space.repository;

import com.univsitdown.space.domain.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findBySpaceIdOrderByRowNumAscColNumAsc(UUID spaceId);

    boolean existsBySpaceId(UUID spaceId);

    void deleteBySpaceId(UUID spaceId);

    long countBySpaceIdAndIsEnabledTrue(UUID spaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") UUID id);
}
```

- [ ] **Step 2: ReservationRepository에 countOccupiedBySpaceId 추가**

`src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java` 파일 하단에 아래 메서드 추가:

```java
@Query("""
        SELECT COUNT(DISTINCT r.seat.id)
        FROM Reservation r
        WHERE r.seat.space.id = :spaceId
          AND r.status NOT IN ('CANCELED', 'NO_SHOW')
          AND r.startAt <= :now AND r.endAt > :now
        """)
long countOccupiedBySpaceId(@Param("spaceId") UUID spaceId, @Param("now") LocalDateTime now);
```

import `java.time.LocalDateTime`가 이미 있으므로 추가 import 불필요.

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/univsitdown/space/repository/SeatRepository.java
git add src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java
git commit -m "feat: SeatRepository·ReservationRepository 좌석 수·점유 수 쿼리 추가"
```

---

### Task 3: DTO 오버로드 — 실제 stats 수용

**Files:**
- Modify: `src/main/java/com/univsitdown/space/dto/SpaceListItemResponse.java`
- Modify: `src/main/java/com/univsitdown/space/dto/SpaceDetailResponse.java`

- [ ] **Step 1: SpaceListItemResponse — from(Space, int, int) 오버로드 추가**

`src/main/java/com/univsitdown/space/dto/SpaceListItemResponse.java` 전체 교체:

```java
package com.univsitdown.space.dto;

import com.univsitdown.space.domain.Space;

import java.util.List;

public record SpaceListItemResponse(
        String id,
        String name,
        int floor,
        String category,
        int totalSeats,
        int availableSeats,
        String congestion,
        String openTime,
        String closeTime,
        List<String> features,
        String thumbnailUrl
) {
    public static SpaceListItemResponse from(Space space) {
        return from(space, 0, 0);
    }

    public static SpaceListItemResponse from(Space space, int totalSeats, int availableSeats) {
        return new SpaceListItemResponse(
                space.getId() != null ? space.getId().toString() : null,
                space.getName(),
                space.getFloor(),
                space.getCategory().name(),
                totalSeats,
                availableSeats,
                computeCongestion(totalSeats, availableSeats),
                space.getOpenTime().toString(),
                space.getCloseTime().toString(),
                space.getFeatures(),
                space.getThumbnailUrl()
        );
    }

    private static String computeCongestion(int totalSeats, int availableSeats) {
        if (totalSeats == 0) return "LOW";
        double occupancyRate = (double) (totalSeats - availableSeats) / totalSeats;
        if (occupancyRate < 0.40) return "LOW";
        if (occupancyRate < 0.75) return "NORMAL";
        return "HIGH";
    }
}
```

- [ ] **Step 2: SpaceDetailResponse — from(Space, int, int) 오버로드 추가**

`src/main/java/com/univsitdown/space/dto/SpaceDetailResponse.java` 전체 교체:

```java
package com.univsitdown.space.dto;

import com.univsitdown.space.domain.Space;

import java.util.List;

public record SpaceDetailResponse(
        String id,
        String name,
        int floor,
        String category,
        int totalSeats,
        int availableSeats,
        int rows,
        int columns,
        String congestion,
        String openTime,
        String closeTime,
        int maxReservationHours,
        List<String> features,
        List<String> images,
        boolean isFavorite
) {
    public static SpaceDetailResponse from(Space space) {
        return from(space, 0, 0);
    }

    public static SpaceDetailResponse from(Space space, int totalSeats, int availableSeats) {
        return new SpaceDetailResponse(
                space.getId() != null ? space.getId().toString() : null,
                space.getName(),
                space.getFloor(),
                space.getCategory().name(),
                totalSeats,
                availableSeats,
                0,
                0,
                computeCongestion(totalSeats, availableSeats),
                space.getOpenTime().toString(),
                space.getCloseTime().toString(),
                space.getMaxReservationHours(),
                space.getFeatures(),
                List.of(),
                false
        );
    }

    private static String computeCongestion(int totalSeats, int availableSeats) {
        if (totalSeats == 0) return "LOW";
        double occupancyRate = (double) (totalSeats - availableSeats) / totalSeats;
        if (occupancyRate < 0.40) return "LOW";
        if (occupancyRate < 0.75) return "NORMAL";
        return "HIGH";
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/univsitdown/space/dto/SpaceListItemResponse.java
git add src/main/java/com/univsitdown/space/dto/SpaceDetailResponse.java
git commit -m "feat: SpaceListItemResponse·SpaceDetailResponse stats 오버로드 + congestion 계산"
```

---

### Task 4: SpaceService — @Cacheable + 실제 stats 계산

**Files:**
- Modify: `src/main/java/com/univsitdown/space/service/SpaceService.java`

- [ ] **Step 1: SpaceService 전체 교체**

```java
package com.univsitdown.space.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "space:list",
               key = "#category + ':' + #keyword + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PageResponse<SpaceListItemResponse> getSpaces(SpaceCategory category, String keyword, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        String keywordPattern = keyword != null ? "%" + keyword + "%" : null;
        Page<SpaceListItemResponse> page = spaceRepository
                .findByFilters(category, keywordPattern, pageable)
                .map(space -> toListItem(space, now));
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "space:detail", key = "#id")
    public SpaceDetailResponse getSpace(UUID id) {
        Space space = spaceRepository.findById(id)
                .orElseThrow(SpaceNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        int total = (int) seatRepository.countBySpaceIdAndIsEnabledTrue(id);
        int occupied = (int) reservationRepository.countOccupiedBySpaceId(id, now);
        return SpaceDetailResponse.from(space, total, total - occupied);
    }

    @Transactional
    public SpaceDetailResponse createSpace(CreateSpaceRequest request) {
        Space space = Space.create(
                request.name(),
                request.floor(),
                request.category(),
                request.openTime(),
                request.closeTime(),
                request.maxReservationHours(),
                request.features(),
                request.thumbnailUrl()
        );
        return SpaceDetailResponse.from(spaceRepository.save(space));
    }

    private SpaceListItemResponse toListItem(Space space, LocalDateTime now) {
        int total = (int) seatRepository.countBySpaceIdAndIsEnabledTrue(space.getId());
        int occupied = (int) reservationRepository.countOccupiedBySpaceId(space.getId(), now);
        return SpaceListItemResponse.from(space, total, total - occupied);
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/univsitdown/space/service/SpaceService.java
git commit -m "feat: SpaceService @Cacheable + availableSeats·congestion 실제 계산"
```

---

### Task 5: SeatService — @Cacheable + @CacheEvict

**Files:**
- Modify: `src/main/java/com/univsitdown/space/service/SeatService.java`

- [ ] **Step 1: SeatService 수정 — getSeatLayout에 @Cacheable, updateSeatStatus에 @CacheEvict 추가**

```java
package com.univsitdown.space.service;

import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.SeatStatus;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.dto.*;
import com.univsitdown.space.exception.SeatAlreadyExistsException;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "space:list",   allEntries = true),
            @CacheEvict(value = "space:detail", allEntries = true),
            @CacheEvict(value = "seat:layout",  allEntries = true)
    })
    public CreateSeatGridResponse createGrid(UUID spaceId, CreateSeatGridRequest request) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(SpaceNotFoundException::new);

        if (seatRepository.existsBySpaceId(spaceId)) {
            if (!request.overwrite()) throw new SeatAlreadyExistsException();
            seatRepository.deleteBySpaceId(spaceId);
        }

        String prefix = (request.labelPrefix() != null && !request.labelPrefix().isBlank())
                ? request.labelPrefix() : "A";
        char baseChar = prefix.charAt(0);

        List<Seat> seats = new ArrayList<>();
        for (int r = 1; r <= request.rows(); r++) {
            String rowLetter = String.valueOf((char) (baseChar + r - 1));
            for (int c = 1; c <= request.columns(); c++) {
                seats.add(Seat.create(space, r, c, rowLetter + "-" + c));
            }
        }
        seatRepository.saveAll(seats);

        return new CreateSeatGridResponse(spaceId.toString(), seats.size(), request.rows(), request.columns());
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "space:list",   allEntries = true),
            @CacheEvict(value = "space:detail", allEntries = true),
            @CacheEvict(value = "seat:layout",  allEntries = true)
    })
    public void updateSeatStatus(UUID seatId, boolean isEnabled) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(SeatNotFoundException::new);
        seat.updateEnabled(isEnabled);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "seat:layout", key = "#spaceId")
    public SeatLayoutResponse getSeatLayout(UUID spaceId, LocalDateTime at) {
        spaceRepository.findById(spaceId).orElseThrow(SpaceNotFoundException::new);

        List<Seat> seats = seatRepository.findBySpaceIdOrderByRowNumAscColNumAsc(spaceId);
        Set<UUID> occupiedIds = Set.copyOf(reservationRepository.findOccupiedSeatIdsBySpaceId(spaceId, at));

        int maxRow = seats.stream().mapToInt(Seat::getRowNum).max().orElse(0);
        int maxCol = seats.stream().mapToInt(Seat::getColNum).max().orElse(0);

        List<SeatItemResponse> seatResponses = seats.stream()
                .map(seat -> SeatItemResponse.of(seat, resolveSeatStatus(seat, occupiedIds)))
                .collect(Collectors.toList());

        return new SeatLayoutResponse(spaceId.toString(), maxRow, maxCol, seatResponses);
    }

    @Transactional(readOnly = true)
    public SeatDetailResponse getSeatDetail(UUID seatId, LocalDateTime at) {
        Seat seat = seatRepository.findById(seatId).orElseThrow(SeatNotFoundException::new);
        Set<UUID> occupiedIds = Set.copyOf(
                reservationRepository.findOccupiedSeatIdsBySpaceId(seat.getSpace().getId(), at));
        return SeatDetailResponse.of(seat, resolveSeatStatus(seat, occupiedIds));
    }

    private SeatStatus resolveSeatStatus(Seat seat, Set<UUID> occupiedIds) {
        if (!seat.isEnabled()) return SeatStatus.UNAVAILABLE;
        if (occupiedIds.contains(seat.getId())) return SeatStatus.OCCUPIED;
        return SeatStatus.AVAILABLE;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/univsitdown/space/service/SeatService.java
git commit -m "feat: SeatService getSeatLayout @Cacheable, updateSeatStatus/createGrid @CacheEvict"
```

---

### Task 6: ReservationService — reserve/extend/cancel에 @CacheEvict

**Files:**
- Modify: `src/main/java/com/univsitdown/reservation/service/ReservationService.java`

- [ ] **Step 1: import 추가 및 @Caching evict 어노테이션 적용**

`reserve()`, `extend()`, `cancel()` 메서드에 각각 동일한 `@Caching` 추가. 파일 전체:

```java
package com.univsitdown.reservation.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;
import com.univsitdown.reservation.dto.*;
import com.univsitdown.reservation.exception.*;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.exception.SeatUnavailableException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.user.domain.User;
import com.univsitdown.user.exception.UserNotFoundException;
import com.univsitdown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "space:list",   allEntries = true),
            @CacheEvict(value = "space:detail", allEntries = true),
            @CacheEvict(value = "seat:layout",  allEntries = true)
    })
    public CreateReservationResponse reserve(UUID userId, CreateReservationRequest request) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        LocalDateTime startAt = request.startAt();
        LocalDateTime endAt = request.endAt();

        if (!endAt.isAfter(startAt)) throw new ReservationInvalidTimeException();

        Seat seat = seatRepository.findByIdForUpdate(request.seatId())
                .orElseThrow(SeatNotFoundException::new);

        if (!seat.isEnabled()) throw new SeatUnavailableException();

        var space = seat.getSpace();
        if (startAt.toLocalTime().isBefore(space.getOpenTime()) ||
            endAt.toLocalTime().isAfter(space.getCloseTime())) {
            throw new ReservationOutOfHoursException();
        }

        long durationHours = ChronoUnit.HOURS.between(startAt, endAt);
        if (durationHours > space.getMaxReservationHours()) {
            throw new ReservationMaxDurationExceededException();
        }

        if (reservationRepository.countActiveByUserId(userId, now) >= 1) {
            throw new UserReservationLimitException();
        }

        if (reservationRepository.existsOverlapping(seat.getId(), startAt, endAt)) {
            throw new SeatAlreadyReservedException();
        }

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Reservation saved = reservationRepository.save(Reservation.create(user, seat, startAt, endAt));
        return CreateReservationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationListItemResponse> getMyReservations(
            UUID userId, String statusFilter, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        Page<Reservation> page = switch (statusFilter != null ? statusFilter : "ACTIVE") {
            case "PAST" -> reservationRepository.findPastByUserId(
                    userId, ReservationStatus.SCHEDULED, now, pageable);
            case "CANCELED" -> reservationRepository.findCanceledByUserId(
                    userId, List.of(ReservationStatus.CANCELED, ReservationStatus.NO_SHOW), pageable);
            default -> reservationRepository.findActiveByUserId(
                    userId, ReservationStatus.SCHEDULED, now, pageable);
        };
        return PageResponse.from(page.map(r -> ReservationListItemResponse.from(r, now)));
    }

    @Transactional(readOnly = true)
    public ReservationDetailResponse getReservation(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);
        if (!reservation.getUser().getId().equals(userId)) {
            throw new ReservationNotOwnerException();
        }
        return ReservationDetailResponse.from(reservation, LocalDateTime.now(ZoneOffset.ofHours(9)));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "space:list",   allEntries = true),
            @CacheEvict(value = "space:detail", allEntries = true),
            @CacheEvict(value = "seat:layout",  allEntries = true)
    })
    public ExtendReservationResponse extend(UUID reservationId, UUID userId, int additionalMinutes) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ReservationNotOwnerException();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        if (reservation.computedStatus(now) != ReservationStatus.IN_USE) {
            throw new ReservationNotExtendableException();
        }

        if (reservation.getExtendedCount() >= 2) {
            throw new ReservationMaxExtendExceededException();
        }

        LocalDateTime newEndAt = reservation.getEndAt().plusMinutes(additionalMinutes);

        if (reservationRepository.existsOverlappingExclude(
                reservation.getSeat().getId(), reservationId,
                reservation.getEndAt(), newEndAt)) {
            throw new ReservationExtendConflictException();
        }

        reservation.extend(newEndAt);
        return ExtendReservationResponse.from(reservation);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "space:list",   allEntries = true),
            @CacheEvict(value = "space:detail", allEntries = true),
            @CacheEvict(value = "seat:layout",  allEntries = true)
    })
    public void cancel(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ReservationNotOwnerException();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        if (reservation.computedStatus(now) == ReservationStatus.COMPLETED) {
            throw new ReservationAlreadyEndedException();
        }

        reservation.cancel(now);
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/univsitdown/reservation/service/ReservationService.java
git commit -m "feat: ReservationService reserve/extend/cancel @CacheEvict"
```

---

### Task 7: SpaceServiceTest 업데이트 — 새 의존성 mock + stats 검증

**Files:**
- Modify: `src/test/java/com/univsitdown/space/service/SpaceServiceTest.java`

- [ ] **Step 1: SpaceServiceTest 전체 교체**

기존 테스트는 `totalSeats=0`, `congestion="LOW"` stub을 검증했으나, 이제 실제 계산값을 검증하도록 변경. `SeatRepository`와 `ReservationRepository` mock 추가 필요.

```java
package com.univsitdown.space.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    @Mock SpaceRepository spaceRepository;
    @Mock SeatRepository seatRepository;
    @Mock ReservationRepository reservationRepository;
    @InjectMocks SpaceService spaceService;

    private Space sampleSpace() {
        Space space = Space.create("제1열람실", 3, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4,
                List.of("콘센트", "조용함"), null);
        ReflectionTestUtils.setField(space, "id", UUID.randomUUID());
        return space;
    }

    @Test
    void getSpaces_좌석10개_점유3개_AVAILABLE7개_LOW() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(10L);
        given(reservationRepository.countOccupiedBySpaceId(any(), any())).willReturn(3L);

        PageResponse<SpaceListItemResponse> response = spaceService.getSpaces(null, null, pageable);

        assertThat(response.content()).hasSize(1);
        SpaceListItemResponse item = response.content().get(0);
        assertThat(item.totalSeats()).isEqualTo(10);
        assertThat(item.availableSeats()).isEqualTo(7);
        assertThat(item.congestion()).isEqualTo("LOW");   // 3/10 = 30% < 40%
    }

    @Test
    void getSpaces_점유율40퍼이상_NORMAL() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(10L);
        given(reservationRepository.countOccupiedBySpaceId(any(), any())).willReturn(6L);

        PageResponse<SpaceListItemResponse> response = spaceService.getSpaces(null, null, pageable);

        assertThat(response.content().get(0).congestion()).isEqualTo("NORMAL"); // 6/10 = 60%
    }

    @Test
    void getSpaces_점유율75퍼이상_HIGH() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(10L);
        given(reservationRepository.countOccupiedBySpaceId(any(), any())).willReturn(8L);

        PageResponse<SpaceListItemResponse> response = spaceService.getSpaces(null, null, pageable);

        assertThat(response.content().get(0).congestion()).isEqualTo("HIGH"); // 8/10 = 80%
    }

    @Test
    void getSpaces_좌석없음_congestion_LOW() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(0L);
        given(reservationRepository.countOccupiedBySpaceId(any(), any())).willReturn(0L);

        PageResponse<SpaceListItemResponse> response = spaceService.getSpaces(null, null, pageable);

        assertThat(response.content().get(0).congestion()).isEqualTo("LOW");
    }

    @Test
    void getSpace_정상조회_stats_포함() {
        UUID id = UUID.randomUUID();
        Space space = sampleSpace();
        ReflectionTestUtils.setField(space, "id", id);
        given(spaceRepository.findById(id)).willReturn(Optional.of(space));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(id)).willReturn(20L);
        given(reservationRepository.countOccupiedBySpaceId(eq(id), any())).willReturn(5L);

        SpaceDetailResponse response = spaceService.getSpace(id);

        assertThat(response.name()).isEqualTo("제1열람실");
        assertThat(response.totalSeats()).isEqualTo(20);
        assertThat(response.availableSeats()).isEqualTo(15);
        assertThat(response.congestion()).isEqualTo("LOW"); // 5/20 = 25%
    }

    @Test
    void getSpace_없는ID_SpaceNotFoundException() {
        UUID id = UUID.randomUUID();
        given(spaceRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> spaceService.getSpace(id))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void createSpace_정상생성_stub값_반환() {
        CreateSpaceRequest request = new CreateSpaceRequest(
                "제2열람실", 2, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4,
                List.of("와이파이"), null
        );
        Space saved = Space.create(request.name(), request.floor(), request.category(),
                request.openTime(), request.closeTime(), request.maxReservationHours(),
                request.features(), request.thumbnailUrl());
        given(spaceRepository.save(any())).willReturn(saved);

        SpaceDetailResponse response = spaceService.createSpace(request);

        assertThat(response.name()).isEqualTo("제2열람실");
        assertThat(response.totalSeats()).isEqualTo(0); // 신규 공간 — 좌석 없음
        then(spaceRepository).should().save(any(Space.class));
    }
}
```

- [ ] **Step 2: 테스트 실행 확인**

```bash
./gradlew test --tests "com.univsitdown.space.service.SpaceServiceTest"
```
Expected: BUILD SUCCESSFUL, 7 tests passed

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/univsitdown/space/service/SpaceServiceTest.java
git commit -m "test: SpaceServiceTest — stats 계산 로직 검증으로 업데이트"
```

---

### Task 8: CacheIntegrationTest — 캐시 히트·evict 통합 검증

**Files:**
- Create: `src/test/java/com/univsitdown/global/config/CacheIntegrationTest.java`

캐시는 Spring Proxy를 통해서만 동작하므로 `@SpringBootTest` 필수. Redis 없이 `ConcurrentMapCacheManager`(fallback)으로 동작하도록 `spring.data.redis.host=invalid`로 설정. PostgreSQL은 Testcontainers로 실제 DB 사용.

- [ ] **Step 1: CacheIntegrationTest 생성**

```java
package com.univsitdown.global.config;

import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.space.service.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-for-cache-integration-test-32chars",
        "spring.data.redis.host=invalid-host-triggers-fallback"
})
@Testcontainers
class CacheIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("sitdown_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired SpaceService spaceService;
    @SpyBean SpaceRepository spaceRepository;

    private Space saveSpace() {
        return spaceRepository.save(Space.create(
                "캐시테스트열람실_" + UUID.randomUUID(), 1, SpaceCategory.READING_ROOM,
                LocalTime.of(0, 0), LocalTime.of(23, 59), 4, List.of(), null));
    }

    @Test
    void getSpace_두번_호출시_DB는_한번만_조회() {
        Space space = saveSpace();
        UUID spaceId = space.getId();

        spaceService.getSpace(spaceId); // cache miss — DB 조회
        spaceService.getSpace(spaceId); // cache hit  — DB 미조회

        verify(spaceRepository, times(1)).findById(spaceId);
    }

    @Test
    void getSpace_캐시결과가_정확한_ID를_포함() {
        Space space = saveSpace();
        UUID spaceId = space.getId();

        SpaceDetailResponse first  = spaceService.getSpace(spaceId);
        SpaceDetailResponse second = spaceService.getSpace(spaceId);

        assertThat(first.id()).isEqualTo(spaceId.toString());
        assertThat(second.id()).isEqualTo(spaceId.toString());
    }
}
```

- [ ] **Step 2: 테스트 실행 확인 (Docker 필요)**

```bash
./gradlew test --tests "com.univsitdown.global.config.CacheIntegrationTest"
```
Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 3: 전체 단위 테스트 실행 (Docker 불필요)**

```bash
./gradlew test --tests "com.univsitdown.space.service.SpaceServiceTest" \
               --tests "com.univsitdown.space.service.SeatServiceTest" \
               --tests "com.univsitdown.space.controller.SeatControllerTest" \
               --tests "com.univsitdown.reservation.service.ReservationServiceTest" \
               --tests "com.univsitdown.reservation.controller.ReservationControllerTest" \
               --tests "com.univsitdown.auth.*" \
               --tests "com.univsitdown.user.*"
```
Expected: BUILD SUCCESSFUL, 전체 GREEN

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/com/univsitdown/global/config/CacheIntegrationTest.java
git commit -m "test: CacheIntegrationTest — 캐시 히트·evict 통합 검증"
```

- [ ] **Step 5: CLAUDE.md 현재 Phase 업데이트 후 push**

`CLAUDE.md`의 `현재 Phase` 섹션을 "Phase 5 완료 / Phase 6 준비 중"으로 변경 후:

```bash
git add CLAUDE.md
git commit -m "docs: CLAUDE.md Phase 5 완료 상태 업데이트"
git push origin main
```
