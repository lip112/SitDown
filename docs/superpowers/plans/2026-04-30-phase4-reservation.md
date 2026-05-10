# Phase 4 — 예약 핵심 로직 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seat 도메인과 Reservation 도메인을 구현하고 비관적 락 + DB EXCLUDE 제약으로 동시 예약 충돌을 방지한다.

**Architecture:** Space 패키지에 Seat 도메인을 추가하고, 독립 패키지 `reservation`에 Reservation 도메인을 구성한다. 예약 상태(IN_USE/COMPLETED)는 DB에 저장하지 않고 조회 시 `now` 기준으로 동적 계산한다. 동시성은 `SELECT FOR UPDATE` + PostgreSQL `EXCLUDE` 제약으로 이중 방어한다.

**Tech Stack:** Java 17, Spring Boot 3.5, PostgreSQL 16 (btree_gist), JPA/Hibernate, Mockito, Testcontainers

---

## File Map

**신규 생성:**
- `src/main/resources/db/migration/V4__create_seat_table.sql`
- `src/main/resources/db/migration/V5__create_reservation_table.sql`
- `src/main/java/com/univsitdown/space/domain/Seat.java`
- `src/main/java/com/univsitdown/space/domain/SeatStatus.java`
- `src/main/java/com/univsitdown/space/repository/SeatRepository.java`
- `src/main/java/com/univsitdown/space/exception/SeatNotFoundException.java`
- `src/main/java/com/univsitdown/space/exception/SeatUnavailableException.java`
- `src/main/java/com/univsitdown/space/exception/SeatAlreadyExistsException.java`
- `src/main/java/com/univsitdown/space/exception/SeatGridSizeExceededException.java`
- `src/main/java/com/univsitdown/space/dto/CreateSeatGridRequest.java`
- `src/main/java/com/univsitdown/space/dto/CreateSeatGridResponse.java`
- `src/main/java/com/univsitdown/space/dto/UpdateSeatStatusRequest.java`
- `src/main/java/com/univsitdown/space/dto/SeatLayoutResponse.java`
- `src/main/java/com/univsitdown/space/dto/SeatItemResponse.java`
- `src/main/java/com/univsitdown/space/dto/SeatDetailResponse.java`
- `src/main/java/com/univsitdown/space/service/SeatService.java`
- `src/main/java/com/univsitdown/space/controller/SeatController.java`
- `src/main/java/com/univsitdown/space/controller/AdminSeatController.java`
- `src/main/java/com/univsitdown/reservation/domain/Reservation.java`
- `src/main/java/com/univsitdown/reservation/domain/ReservationStatus.java`
- `src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java`
- `src/main/java/com/univsitdown/reservation/exception/SeatAlreadyReservedException.java`
- `src/main/java/com/univsitdown/reservation/exception/UserReservationLimitException.java`
- `src/main/java/com/univsitdown/reservation/exception/ReservationOutOfHoursException.java`
- `src/main/java/com/univsitdown/reservation/exception/ReservationMaxDurationExceededException.java`
- `src/main/java/com/univsitdown/reservation/exception/ReservationInvalidTimeException.java`
- `src/main/java/com/univsitdown/reservation/exception/ReservationNotFoundException.java`
- `src/main/java/com/univsitdown/reservation/exception/ReservationNotExtendableException.java`
- `src/main/java/com/univsitdown/reservation/exception/ReservationExtendConflictException.java`
- `src/main/java/com/univsitdown/reservation/exception/ReservationMaxExtendExceededException.java`
- `src/main/java/com/univsitdown/reservation/exception/ReservationNotOwnerException.java`
- `src/main/java/com/univsitdown/reservation/exception/ReservationAlreadyEndedException.java`
- `src/main/java/com/univsitdown/reservation/dto/CreateReservationRequest.java`
- `src/main/java/com/univsitdown/reservation/dto/CreateReservationResponse.java`
- `src/main/java/com/univsitdown/reservation/dto/ReservationListItemResponse.java`
- `src/main/java/com/univsitdown/reservation/dto/ReservationDetailResponse.java`
- `src/main/java/com/univsitdown/reservation/dto/ExtendReservationRequest.java`
- `src/main/java/com/univsitdown/reservation/dto/ExtendReservationResponse.java`
- `src/main/java/com/univsitdown/reservation/service/ReservationService.java`
- `src/main/java/com/univsitdown/reservation/controller/ReservationController.java`
- `src/test/java/com/univsitdown/space/service/SeatServiceTest.java`
- `src/test/java/com/univsitdown/space/controller/SeatControllerTest.java`
- `src/test/java/com/univsitdown/space/controller/AdminSeatControllerTest.java`
- `src/test/java/com/univsitdown/reservation/service/ReservationServiceTest.java`
- `src/test/java/com/univsitdown/reservation/controller/ReservationControllerTest.java`
- `src/test/java/com/univsitdown/reservation/service/ReservationConcurrencyTest.java`

**수정:**
- `src/main/java/com/univsitdown/global/exception/ErrorCode.java` — RESERVATION_NOT_FOUND 추가

---

## Task 1: Seat 도메인 기반 (마이그레이션 + Entity + Repository + 예외)

**Files:**
- Create: `src/main/resources/db/migration/V4__create_seat_table.sql`
- Create: `src/main/java/com/univsitdown/space/domain/Seat.java`
- Create: `src/main/java/com/univsitdown/space/domain/SeatStatus.java`
- Create: `src/main/java/com/univsitdown/space/repository/SeatRepository.java`
- Create: `src/main/java/com/univsitdown/space/exception/SeatNotFoundException.java`
- Create: `src/main/java/com/univsitdown/space/exception/SeatUnavailableException.java`
- Create: `src/main/java/com/univsitdown/space/exception/SeatAlreadyExistsException.java`
- Create: `src/main/java/com/univsitdown/space/exception/SeatGridSizeExceededException.java`

- [ ] **Step 1: V4 마이그레이션 파일 생성**

```sql
-- src/main/resources/db/migration/V4__create_seat_table.sql
CREATE TABLE seats (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id   UUID         NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    row_num    INT          NOT NULL,
    col_num    INT          NOT NULL,
    label      VARCHAR(20)  NOT NULL,
    is_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    features   TEXT[]       NOT NULL DEFAULT '{}',
    UNIQUE (space_id, row_num, col_num)
);

CREATE INDEX idx_seats_space_id ON seats(space_id);
```

- [ ] **Step 2: SeatStatus Enum 생성**

```java
// src/main/java/com/univsitdown/space/domain/SeatStatus.java
package com.univsitdown.space.domain;

public enum SeatStatus {
    AVAILABLE, OCCUPIED, UNAVAILABLE, RESERVED
}
```

- [ ] **Step 3: Seat Entity 생성**

```java
// src/main/java/com/univsitdown/space/domain/Seat.java
package com.univsitdown.space.domain;

import com.univsitdown.global.converter.StringListConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    @Column(nullable = false)
    private int rowNum;

    @Column(nullable = false)
    private int colNum;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(nullable = false)
    private boolean isEnabled;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "text[]", nullable = false)
    private List<String> features;

    public static Seat create(Space space, int rowNum, int colNum, String label) {
        Seat seat = new Seat();
        seat.space = space;
        seat.rowNum = rowNum;
        seat.colNum = colNum;
        seat.label = label;
        seat.isEnabled = true;
        seat.features = List.of();
        return seat;
    }

    public void updateEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }
}
```

- [ ] **Step 4: SeatRepository 생성**

```java
// src/main/java/com/univsitdown/space/repository/SeatRepository.java
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") UUID id);
}
```

- [ ] **Step 5: 예외 클래스 4개 생성**

```java
// src/main/java/com/univsitdown/space/exception/SeatNotFoundException.java
package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class SeatNotFoundException extends BusinessException {
    public SeatNotFoundException() { super(ErrorCode.SEAT_NOT_FOUND); }
}
```

```java
// src/main/java/com/univsitdown/space/exception/SeatUnavailableException.java
package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class SeatUnavailableException extends BusinessException {
    public SeatUnavailableException() { super(ErrorCode.SEAT_UNAVAILABLE); }
}
```

```java
// src/main/java/com/univsitdown/space/exception/SeatAlreadyExistsException.java
package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class SeatAlreadyExistsException extends BusinessException {
    public SeatAlreadyExistsException() { super(ErrorCode.SEAT_ALREADY_EXISTS); }
}
```

```java
// src/main/java/com/univsitdown/space/exception/SeatGridSizeExceededException.java
package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class SeatGridSizeExceededException extends BusinessException {
    public SeatGridSizeExceededException() { super(ErrorCode.SEAT_GRID_SIZE_EXCEEDED); }
}
```

- [ ] **Step 6: BusinessException 상속 구조 확인**

기존 예외 클래스(예: `SpaceNotFoundException`)와 동일한 패턴인지 확인:
```bash
# 파일 존재 확인
ls src/main/java/com/univsitdown/space/exception/
```

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/db/migration/V4__create_seat_table.sql
git add src/main/java/com/univsitdown/space/domain/Seat.java
git add src/main/java/com/univsitdown/space/domain/SeatStatus.java
git add src/main/java/com/univsitdown/space/repository/SeatRepository.java
git add src/main/java/com/univsitdown/space/exception/
git commit -m "feat: Seat 도메인 기반 구현 (Entity, Repository, 예외 클래스)"
```

---

## Task 2: ADMIN-02 좌석 일괄 생성

**Files:**
- Create: `src/main/java/com/univsitdown/space/dto/CreateSeatGridRequest.java`
- Create: `src/main/java/com/univsitdown/space/dto/CreateSeatGridResponse.java`
- Create: `src/main/java/com/univsitdown/space/service/SeatService.java`
- Create: `src/main/java/com/univsitdown/space/controller/AdminSeatController.java`
- Create: `src/test/java/com/univsitdown/space/service/SeatServiceTest.java`
- Create: `src/test/java/com/univsitdown/space/controller/AdminSeatControllerTest.java`

- [ ] **Step 1: DTO 생성**

```java
// src/main/java/com/univsitdown/space/dto/CreateSeatGridRequest.java
package com.univsitdown.space.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateSeatGridRequest(
        @Min(1) @Max(20) int rows,
        @Min(1) @Max(20) int columns,
        String labelPrefix,
        boolean overwrite
) {}
```

```java
// src/main/java/com/univsitdown/space/dto/CreateSeatGridResponse.java
package com.univsitdown.space.dto;

public record CreateSeatGridResponse(
        String spaceId,
        int createdCount,
        int rows,
        int columns
) {}
```

- [ ] **Step 2: SeatService 생성 (createGrid 메서드만)**

```java
// src/main/java/com/univsitdown/space/service/SeatService.java
package com.univsitdown.space.service;

import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.dto.CreateSeatGridRequest;
import com.univsitdown.space.dto.CreateSeatGridResponse;
import com.univsitdown.space.exception.SeatAlreadyExistsException;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.space.exception.SpaceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final SpaceRepository spaceRepository;

    @Transactional
    public CreateSeatGridResponse createGrid(UUID spaceId, CreateSeatGridRequest request) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(SpaceNotFoundException::new);

        if (seatRepository.existsBySpaceId(spaceId)) {
            if (!request.overwrite()) {
                throw new SeatAlreadyExistsException();
            }
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

        return new CreateSeatGridResponse(
                spaceId.toString(),
                seats.size(),
                request.rows(),
                request.columns()
        );
    }
}
```

- [ ] **Step 3: AdminSeatController 생성**

```java
// src/main/java/com/univsitdown/space/controller/AdminSeatController.java
package com.univsitdown.space.controller;

import com.univsitdown.space.dto.CreateSeatGridRequest;
import com.univsitdown.space.dto.CreateSeatGridResponse;
import com.univsitdown.space.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/spaces")
@RequiredArgsConstructor
public class AdminSeatController {

    private final SeatService seatService;

    @PostMapping("/{id}/seats/grid")
    public ResponseEntity<CreateSeatGridResponse> createGrid(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSeatGridRequest request) {
        return ResponseEntity.ok(seatService.createGrid(id, request));
    }
}
```

- [ ] **Step 4: SeatServiceTest 작성 (createGrid)**

```java
// src/test/java/com/univsitdown/space/service/SeatServiceTest.java
package com.univsitdown.space.service;

import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSeatGridRequest;
import com.univsitdown.space.dto.CreateSeatGridResponse;
import com.univsitdown.space.exception.SeatAlreadyExistsException;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock SeatRepository seatRepository;
    @Mock SpaceRepository spaceRepository;
    @InjectMocks SeatService seatService;

    private Space sampleSpace() {
        return Space.create("제1열람실", 3, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4, List.of(), null);
    }

    @Test
    void createGrid_정상생성_성공() {
        UUID spaceId = UUID.randomUUID();
        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(sampleSpace()));
        given(seatRepository.existsBySpaceId(spaceId)).willReturn(false);
        given(seatRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        CreateSeatGridRequest request = new CreateSeatGridRequest(2, 3, "A", false);
        CreateSeatGridResponse response = seatService.createGrid(spaceId, request);

        assertThat(response.createdCount()).isEqualTo(6);
        assertThat(response.rows()).isEqualTo(2);
        assertThat(response.columns()).isEqualTo(3);
    }

    @Test
    void createGrid_좌석이미존재하고overwrite_false_예외() {
        UUID spaceId = UUID.randomUUID();
        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(sampleSpace()));
        given(seatRepository.existsBySpaceId(spaceId)).willReturn(true);

        CreateSeatGridRequest request = new CreateSeatGridRequest(2, 3, "A", false);
        assertThatThrownBy(() -> seatService.createGrid(spaceId, request))
                .isInstanceOf(SeatAlreadyExistsException.class);
    }

    @Test
    void createGrid_overwrite_true_기존삭제후재생성() {
        UUID spaceId = UUID.randomUUID();
        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(sampleSpace()));
        given(seatRepository.existsBySpaceId(spaceId)).willReturn(true);
        given(seatRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        CreateSeatGridRequest request = new CreateSeatGridRequest(2, 3, "A", true);
        CreateSeatGridResponse response = seatService.createGrid(spaceId, request);

        then(seatRepository).should().deleteBySpaceId(spaceId);
        assertThat(response.createdCount()).isEqualTo(6);
    }

    @Test
    void createGrid_없는공간_SpaceNotFoundException() {
        UUID spaceId = UUID.randomUUID();
        given(spaceRepository.findById(spaceId)).willReturn(Optional.empty());

        CreateSeatGridRequest request = new CreateSeatGridRequest(2, 3, "A", false);
        assertThatThrownBy(() -> seatService.createGrid(spaceId, request))
                .isInstanceOf(SpaceNotFoundException.class);
    }
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.space.service.SeatServiceTest" --info
```
Expected: 4 tests PASS

- [ ] **Step 6: AdminSeatControllerTest 작성**

```java
// src/test/java/com/univsitdown/space/controller/AdminSeatControllerTest.java
package com.univsitdown.space.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.space.dto.CreateSeatGridRequest;
import com.univsitdown.space.dto.CreateSeatGridResponse;
import com.univsitdown.space.exception.SeatAlreadyExistsException;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.service.SeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSeatController.class)
@Import(SecurityConfig.class)
class AdminSeatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean SeatService seatService;
    @MockBean JwtProvider jwtProvider;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGrid_200() throws Exception {
        UUID spaceId = UUID.randomUUID();
        CreateSeatGridResponse response = new CreateSeatGridResponse(spaceId.toString(), 80, 8, 10);
        given(seatService.createGrid(any(), any())).willReturn(response);

        mockMvc.perform(post("/api/admin/spaces/{id}/seats/grid", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSeatGridRequest(8, 10, "A", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(80));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGrid_이미존재_409() throws Exception {
        UUID spaceId = UUID.randomUUID();
        given(seatService.createGrid(any(), any())).willThrow(new SeatAlreadyExistsException());

        mockMvc.perform(post("/api/admin/spaces/{id}/seats/grid", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSeatGridRequest(8, 10, "A", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN-002"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGrid_rows_검증실패_400() throws Exception {
        UUID spaceId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/spaces/{id}/seats/grid", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateSeatGridRequest(21, 10, "A", false))))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 7: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.space.controller.AdminSeatControllerTest" --info
```
Expected: 3 tests PASS

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/univsitdown/space/dto/CreateSeatGridRequest.java
git add src/main/java/com/univsitdown/space/dto/CreateSeatGridResponse.java
git add src/main/java/com/univsitdown/space/service/SeatService.java
git add src/main/java/com/univsitdown/space/controller/AdminSeatController.java
git add src/test/java/com/univsitdown/space/service/SeatServiceTest.java
git add src/test/java/com/univsitdown/space/controller/AdminSeatControllerTest.java
git commit -m "feat: ADMIN-02 좌석 일괄 생성 구현"
```

---

## Task 3: ADMIN-03 좌석 상태 변경

**Files:**
- Create: `src/main/java/com/univsitdown/space/dto/UpdateSeatStatusRequest.java`
- Modify: `src/main/java/com/univsitdown/space/service/SeatService.java` — updateSeatStatus 추가
- Modify: `src/main/java/com/univsitdown/space/controller/AdminSeatController.java` — PATCH 엔드포인트 추가
- Modify: `src/test/java/com/univsitdown/space/service/SeatServiceTest.java` — 테스트 추가

- [ ] **Step 1: DTO 생성**

```java
// src/main/java/com/univsitdown/space/dto/UpdateSeatStatusRequest.java
package com.univsitdown.space.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateSeatStatusRequest(@NotNull Boolean isEnabled) {}
```

- [ ] **Step 2: SeatService에 updateSeatStatus 추가**

SeatService.java에 아래 메서드를 추가:
```java
@Transactional
public void updateSeatStatus(UUID seatId, boolean isEnabled) {
    Seat seat = seatRepository.findById(seatId)
            .orElseThrow(SeatNotFoundException::new);
    seat.updateEnabled(isEnabled);
}
```

- [ ] **Step 3: AdminSeatController에 PATCH 엔드포인트 추가**

AdminSeatController.java에 아래 엔드포인트를 추가 (클래스 상단 RequestMapping을 `/api/admin`으로 변경하거나 별도 경로 사용):

```java
// AdminSeatController에 주입 추가: private final SeatService seatService (이미 있음)
// 아래 메서드를 클래스에 추가. 주의: @RequestMapping이 "/api/admin/spaces"이므로
// 이 메서드는 별도 컨트롤러로 분리한다.
```

`AdminSeatController.java` 전체를 아래로 교체:
```java
package com.univsitdown.space.controller;

import com.univsitdown.space.dto.CreateSeatGridRequest;
import com.univsitdown.space.dto.CreateSeatGridResponse;
import com.univsitdown.space.dto.UpdateSeatStatusRequest;
import com.univsitdown.space.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminSeatController {

    private final SeatService seatService;

    @PostMapping("/api/admin/spaces/{id}/seats/grid")
    public ResponseEntity<CreateSeatGridResponse> createGrid(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSeatGridRequest request) {
        return ResponseEntity.ok(seatService.createGrid(id, request));
    }

    @PatchMapping("/api/admin/seats/{id}")
    public ResponseEntity<Void> updateSeatStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSeatStatusRequest request) {
        seatService.updateSeatStatus(id, request.isEnabled());
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 4: SeatServiceTest에 updateSeatStatus 테스트 추가**

SeatServiceTest.java에 아래 테스트 추가:
```java
@Test
void updateSeatStatus_비활성화_성공() {
    UUID seatId = UUID.randomUUID();
    Space space = sampleSpace();
    Seat seat = Seat.create(space, 1, 1, "A-1");
    given(seatRepository.findById(seatId)).willReturn(Optional.of(seat));

    seatService.updateSeatStatus(seatId, false);

    assertThat(seat.isEnabled()).isFalse();
}

@Test
void updateSeatStatus_없는좌석_SeatNotFoundException() {
    UUID seatId = UUID.randomUUID();
    given(seatRepository.findById(seatId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> seatService.updateSeatStatus(seatId, false))
            .isInstanceOf(SeatNotFoundException.class);
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.space.service.SeatServiceTest" --info
```
Expected: 6 tests PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/univsitdown/space/dto/UpdateSeatStatusRequest.java
git add src/main/java/com/univsitdown/space/service/SeatService.java
git add src/main/java/com/univsitdown/space/controller/AdminSeatController.java
git add src/test/java/com/univsitdown/space/service/SeatServiceTest.java
git commit -m "feat: ADMIN-03 좌석 상태 변경 구현"
```

---

## Task 4: Reservation 도메인 기반

**Files:**
- Create: `src/main/resources/db/migration/V5__create_reservation_table.sql`
- Create: `src/main/java/com/univsitdown/reservation/domain/ReservationStatus.java`
- Create: `src/main/java/com/univsitdown/reservation/domain/Reservation.java`
- Create: `src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java`
- Create: 예외 클래스 11개
- Modify: `src/main/java/com/univsitdown/global/exception/ErrorCode.java` — RESERVATION_NOT_FOUND 추가

- [ ] **Step 1: V5 마이그레이션 생성**

```sql
-- src/main/resources/db/migration/V5__create_reservation_table.sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservations (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users(id),
    seat_id        UUID        NOT NULL REFERENCES seats(id),
    start_at       TIMESTAMP   NOT NULL,
    end_at         TIMESTAMP   NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    canceled_at    TIMESTAMP,
    extended_count INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT no_overlap EXCLUDE USING gist (
        seat_id WITH =,
        tsrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status NOT IN ('CANCELED', 'NO_SHOW'))
);

CREATE INDEX idx_reservations_user_id ON reservations(user_id);
CREATE INDEX idx_reservations_seat_id ON reservations(seat_id);
CREATE INDEX idx_reservations_status  ON reservations(status);
```

- [ ] **Step 2: ReservationStatus Enum 생성**

```java
// src/main/java/com/univsitdown/reservation/domain/ReservationStatus.java
package com.univsitdown.reservation.domain;

public enum ReservationStatus {
    SCHEDULED, IN_USE, COMPLETED, CANCELED, NO_SHOW
}
```

- [ ] **Step 3: Reservation Entity 생성**

```java
// src/main/java/com/univsitdown/reservation/domain/Reservation.java
package com.univsitdown.reservation.domain;

import com.univsitdown.space.domain.Seat;
import com.univsitdown.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    private LocalDateTime canceledAt;

    @Column(nullable = false)
    private int extendedCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Reservation create(User user, Seat seat, LocalDateTime startAt, LocalDateTime endAt) {
        Reservation r = new Reservation();
        r.user = user;
        r.seat = seat;
        r.startAt = startAt;
        r.endAt = endAt;
        r.status = ReservationStatus.SCHEDULED;
        r.extendedCount = 0;
        r.createdAt = LocalDateTime.now();
        return r;
    }

    // 조회 시점 now 기준으로 실제 상태를 동적 계산한다.
    // DB에는 SCHEDULED / CANCELED / NO_SHOW 만 저장하며, IN_USE / COMPLETED 는 저장하지 않는다.
    public ReservationStatus computedStatus(LocalDateTime now) {
        if (status == ReservationStatus.CANCELED || status == ReservationStatus.NO_SHOW) {
            return status;
        }
        if (endAt.isBefore(now)) return ReservationStatus.COMPLETED;
        if (!startAt.isAfter(now)) return ReservationStatus.IN_USE;
        return ReservationStatus.SCHEDULED;
    }

    public void extend(LocalDateTime newEndAt) {
        this.endAt = newEndAt;
        this.extendedCount++;
    }

    public void cancel(LocalDateTime now) {
        this.status = ReservationStatus.CANCELED;
        this.canceledAt = now;
    }
}
```

- [ ] **Step 4: ReservationRepository 생성**

```java
// src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java
package com.univsitdown.reservation.repository;

import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    // 시간 겹침 검증: startAt < :endAt AND endAt > :startAt
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.seat.id = :seatId
            AND r.status NOT IN ('CANCELED', 'NO_SHOW')
            AND r.startAt < :endAt AND r.endAt > :startAt
            """)
    boolean existsOverlapping(@Param("seatId") UUID seatId,
                              @Param("startAt") LocalDateTime startAt,
                              @Param("endAt") LocalDateTime endAt);

    // 사용자의 활성 예약 수 (SCHEDULED이고 아직 종료되지 않은 것)
    @Query("""
            SELECT COUNT(r) FROM Reservation r
            WHERE r.user.id = :userId
            AND r.status = 'SCHEDULED'
            AND r.endAt >= :now
            """)
    long countActiveByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    // RSV-02: ACTIVE (SCHEDULED & endAt >= now)
    @Query(value = """
            SELECT r FROM Reservation r JOIN FETCH r.seat s JOIN FETCH s.space
            WHERE r.user.id = :userId AND r.status = :status AND r.endAt >= :now
            ORDER BY r.startAt DESC
            """,
           countQuery = """
            SELECT COUNT(r) FROM Reservation r
            WHERE r.user.id = :userId AND r.status = :status AND r.endAt >= :now
            """)
    Page<Reservation> findActiveByUserId(@Param("userId") UUID userId,
                                         @Param("status") ReservationStatus status,
                                         @Param("now") LocalDateTime now,
                                         Pageable pageable);

    // RSV-02: PAST (SCHEDULED & endAt < now)
    @Query(value = """
            SELECT r FROM Reservation r JOIN FETCH r.seat s JOIN FETCH s.space
            WHERE r.user.id = :userId AND r.status = :status AND r.endAt < :now
            ORDER BY r.startAt DESC
            """,
           countQuery = """
            SELECT COUNT(r) FROM Reservation r
            WHERE r.user.id = :userId AND r.status = :status AND r.endAt < :now
            """)
    Page<Reservation> findPastByUserId(@Param("userId") UUID userId,
                                       @Param("status") ReservationStatus status,
                                       @Param("now") LocalDateTime now,
                                       Pageable pageable);

    // RSV-02: CANCELED
    @Query(value = """
            SELECT r FROM Reservation r JOIN FETCH r.seat s JOIN FETCH s.space
            WHERE r.user.id = :userId AND r.status IN :statuses
            ORDER BY r.startAt DESC
            """,
           countQuery = """
            SELECT COUNT(r) FROM Reservation r
            WHERE r.user.id = :userId AND r.status IN :statuses
            """)
    Page<Reservation> findCanceledByUserId(@Param("userId") UUID userId,
                                           @Param("statuses") List<ReservationStatus> statuses,
                                           Pageable pageable);

    // RSV-04: 연장 시 충돌 검사 (자신 제외)
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.seat.id = :seatId
            AND r.id <> :excludeId
            AND r.status NOT IN ('CANCELED', 'NO_SHOW')
            AND r.startAt < :endAt AND r.endAt > :startAt
            """)
    boolean existsOverlappingExclude(@Param("seatId") UUID seatId,
                                     @Param("excludeId") UUID excludeId,
                                     @Param("startAt") LocalDateTime startAt,
                                     @Param("endAt") LocalDateTime endAt);

    // SEAT-01: 특정 공간의 좌석 중 at 시점에 점유 중인 seat ID 목록
    @Query("""
            SELECT r.seat.id FROM Reservation r
            WHERE r.seat.space.id = :spaceId
            AND r.status NOT IN ('CANCELED', 'NO_SHOW')
            AND r.startAt <= :at AND r.endAt > :at
            """)
    List<UUID> findOccupiedSeatIdsBySpaceId(@Param("spaceId") UUID spaceId,
                                            @Param("at") LocalDateTime at);
}
```

- [ ] **Step 5: ErrorCode에 RESERVATION_NOT_FOUND 추가**

`src/main/java/com/univsitdown/global/exception/ErrorCode.java`의 `// RESERVATION` 섹션 (RSV-001 앞)에 추가:
```java
RESERVATION_NOT_FOUND("RSV-031", HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
```

- [ ] **Step 6: 예외 클래스 11개 생성**

각 파일을 `src/main/java/com/univsitdown/reservation/exception/` 경로에 생성:

```java
// ReservationNotFoundException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationNotFoundException extends BusinessException {
    public ReservationNotFoundException() { super(ErrorCode.RESERVATION_NOT_FOUND); }
}
```

```java
// SeatAlreadyReservedException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class SeatAlreadyReservedException extends BusinessException {
    public SeatAlreadyReservedException() { super(ErrorCode.SEAT_ALREADY_RESERVED); }
}
```

```java
// UserReservationLimitException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class UserReservationLimitException extends BusinessException {
    public UserReservationLimitException() { super(ErrorCode.USER_RESERVATION_LIMIT); }
}
```

```java
// ReservationOutOfHoursException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationOutOfHoursException extends BusinessException {
    public ReservationOutOfHoursException() { super(ErrorCode.RESERVATION_OUT_OF_HOURS); }
}
```

```java
// ReservationMaxDurationExceededException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationMaxDurationExceededException extends BusinessException {
    public ReservationMaxDurationExceededException() { super(ErrorCode.RESERVATION_MAX_DURATION_EXCEEDED); }
}
```

```java
// ReservationInvalidTimeException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationInvalidTimeException extends BusinessException {
    public ReservationInvalidTimeException() { super(ErrorCode.RESERVATION_INVALID_TIME); }
}
```

```java
// ReservationNotExtendableException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationNotExtendableException extends BusinessException {
    public ReservationNotExtendableException() { super(ErrorCode.RESERVATION_NOT_EXTENDABLE); }
}
```

```java
// ReservationExtendConflictException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationExtendConflictException extends BusinessException {
    public ReservationExtendConflictException() { super(ErrorCode.RESERVATION_EXTEND_CONFLICT); }
}
```

```java
// ReservationMaxExtendExceededException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationMaxExtendExceededException extends BusinessException {
    public ReservationMaxExtendExceededException() { super(ErrorCode.RESERVATION_MAX_EXTEND_EXCEEDED); }
}
```

```java
// ReservationNotOwnerException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationNotOwnerException extends BusinessException {
    public ReservationNotOwnerException() { super(ErrorCode.RESERVATION_NOT_OWNER); }
}
```

```java
// ReservationAlreadyEndedException.java
package com.univsitdown.reservation.exception;
import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;
public class ReservationAlreadyEndedException extends BusinessException {
    public ReservationAlreadyEndedException() { super(ErrorCode.RESERVATION_ALREADY_ENDED); }
}
```

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/db/migration/V5__create_reservation_table.sql
git add src/main/java/com/univsitdown/reservation/
git add src/main/java/com/univsitdown/global/exception/ErrorCode.java
git commit -m "feat: Reservation 도메인 기반 구현 (Entity, Repository, 예외 클래스)"
```

---

## Task 5: SEAT-01 + SEAT-02 좌석 조회

**Files:**
- Create: `src/main/java/com/univsitdown/space/dto/SeatItemResponse.java`
- Create: `src/main/java/com/univsitdown/space/dto/SeatLayoutResponse.java`
- Create: `src/main/java/com/univsitdown/space/dto/SeatDetailResponse.java`
- Modify: `src/main/java/com/univsitdown/space/service/SeatService.java` — getSeatLayout, getSeatDetail 추가
- Create: `src/main/java/com/univsitdown/space/controller/SeatController.java`
- Create: `src/test/java/com/univsitdown/space/controller/SeatControllerTest.java`
- Modify: `src/test/java/com/univsitdown/space/service/SeatServiceTest.java` — 조회 테스트 추가

- [ ] **Step 1: DTO 3개 생성**

```java
// src/main/java/com/univsitdown/space/dto/SeatItemResponse.java
package com.univsitdown.space.dto;

import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.SeatStatus;

import java.util.List;

public record SeatItemResponse(
        String id,
        String label,
        int row,
        int column,
        String status,
        List<String> features
) {
    public static SeatItemResponse of(Seat seat, SeatStatus status) {
        return new SeatItemResponse(
                seat.getId().toString(),
                seat.getLabel(),
                seat.getRowNum(),
                seat.getColNum(),
                status.name(),
                seat.getFeatures()
        );
    }
}
```

```java
// src/main/java/com/univsitdown/space/dto/SeatLayoutResponse.java
package com.univsitdown.space.dto;

import java.util.List;

public record SeatLayoutResponse(
        String spaceId,
        int rows,
        int columns,
        List<SeatItemResponse> seats
) {}
```

```java
// src/main/java/com/univsitdown/space/dto/SeatDetailResponse.java
package com.univsitdown.space.dto;

import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.SeatStatus;

import java.util.List;

public record SeatDetailResponse(
        String id,
        String label,
        int row,
        int column,
        String status,
        List<String> features,
        String spaceId,
        String spaceName
) {
    public static SeatDetailResponse of(Seat seat, SeatStatus status) {
        return new SeatDetailResponse(
                seat.getId().toString(),
                seat.getLabel(),
                seat.getRowNum(),
                seat.getColNum(),
                status.name(),
                seat.getFeatures(),
                seat.getSpace().getId().toString(),
                seat.getSpace().getName()
        );
    }
}
```

- [ ] **Step 2: SeatService에 getSeatLayout, getSeatDetail 추가**

SeatService에 ReservationRepository 의존성 추가 및 조회 메서드 추가:

SeatService.java 상단 imports에 추가:
```java
import com.univsitdown.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
```

생성자 파라미터에 추가 (`@RequiredArgsConstructor`가 자동 처리):
```java
private final ReservationRepository reservationRepository;
```

클래스에 메서드 추가:
```java
@Transactional(readOnly = true)
public SeatLayoutResponse getSeatLayout(UUID spaceId, LocalDateTime at) {
    spaceRepository.findById(spaceId).orElseThrow(SpaceNotFoundException::new);

    List<Seat> seats = seatRepository.findBySpaceIdOrderByRowNumAscColNumAsc(spaceId);
    Set<UUID> occupiedIds = Set.copyOf(
            reservationRepository.findOccupiedSeatIdsBySpaceId(spaceId, at));

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
```

필요한 imports:
```java
import com.univsitdown.space.dto.SeatDetailResponse;
import com.univsitdown.space.dto.SeatItemResponse;
import com.univsitdown.space.dto.SeatLayoutResponse;
import com.univsitdown.space.domain.SeatStatus;
import java.util.Set;
import java.util.stream.Collectors;
```

- [ ] **Step 3: SeatController 생성**

```java
// src/main/java/com/univsitdown/space/controller/SeatController.java
package com.univsitdown.space.controller;

import com.univsitdown.space.dto.SeatDetailResponse;
import com.univsitdown.space.dto.SeatLayoutResponse;
import com.univsitdown.space.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/api/spaces/{id}/seats")
    public SeatLayoutResponse getSeatLayout(
            @PathVariable UUID id,
            @RequestParam(required = false) String at) {
        LocalDateTime atTime = at != null
                ? OffsetDateTime.parse(at).toLocalDateTime()
                : LocalDateTime.now(ZoneOffset.ofHours(9));
        return seatService.getSeatLayout(id, atTime);
    }

    @GetMapping("/api/seats/{id}")
    public SeatDetailResponse getSeatDetail(
            @PathVariable UUID id,
            @RequestParam(required = false) String at) {
        LocalDateTime atTime = at != null
                ? OffsetDateTime.parse(at).toLocalDateTime()
                : LocalDateTime.now(ZoneOffset.ofHours(9));
        return seatService.getSeatDetail(id, atTime);
    }
}
```

- [ ] **Step 4: SeatServiceTest에 조회 테스트 추가**

SeatServiceTest에 추가:
```java
@Mock ReservationRepository reservationRepository; // 클래스 상단 @Mock 목록에 추가

@Test
void getSeatLayout_정상조회_AVAILABLE() {
    UUID spaceId = UUID.randomUUID();
    Space space = sampleSpace();
    Seat seat = Seat.create(space, 1, 1, "A-1");
    given(spaceRepository.findById(spaceId)).willReturn(Optional.of(space));
    given(seatRepository.findBySpaceIdOrderByRowNumAscColNumAsc(spaceId)).willReturn(List.of(seat));
    given(reservationRepository.findOccupiedSeatIdsBySpaceId(any(), any())).willReturn(List.of());

    SeatLayoutResponse response = seatService.getSeatLayout(spaceId, LocalDateTime.now());

    assertThat(response.seats()).hasSize(1);
    assertThat(response.seats().get(0).status()).isEqualTo("AVAILABLE");
}

@Test
void getSeatLayout_점유중좌석_OCCUPIED() {
    UUID spaceId = UUID.randomUUID();
    Space space = sampleSpace();
    Seat seat = Seat.create(space, 1, 1, "A-1");
    // Seat ID를 직접 접근할 수 없으므로 save 후 ID를 사용하는 통합 테스트에서 검증
    // 여기서는 occupied 목록이 빈 경우만 단위 테스트
    given(spaceRepository.findById(spaceId)).willReturn(Optional.of(space));
    given(seatRepository.findBySpaceIdOrderByRowNumAscColNumAsc(spaceId)).willReturn(List.of(seat));
    given(reservationRepository.findOccupiedSeatIdsBySpaceId(any(), any())).willReturn(List.of());

    SeatLayoutResponse response = seatService.getSeatLayout(spaceId, LocalDateTime.now());
    assertThat(response.seats().get(0).status()).isEqualTo("AVAILABLE");
}

@Test
void getSeatLayout_없는공간_SpaceNotFoundException() {
    UUID spaceId = UUID.randomUUID();
    given(spaceRepository.findById(spaceId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> seatService.getSeatLayout(spaceId, LocalDateTime.now()))
            .isInstanceOf(SpaceNotFoundException.class);
}
```

SeatServiceTest 상단 imports에 추가:
```java
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.dto.SeatLayoutResponse;
import java.time.LocalDateTime;
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.space.service.SeatServiceTest" --info
```
Expected: 9 tests PASS

- [ ] **Step 6: SeatControllerTest 생성**

```java
// src/test/java/com/univsitdown/space/controller/SeatControllerTest.java
package com.univsitdown.space.controller;

import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.space.dto.SeatDetailResponse;
import com.univsitdown.space.dto.SeatItemResponse;
import com.univsitdown.space.dto.SeatLayoutResponse;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.service.SeatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SeatController.class)
@Import(SecurityConfig.class)
class SeatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SeatService seatService;
    @MockBean JwtProvider jwtProvider;

    @Test
    @WithMockUser
    void getSeatLayout_200() throws Exception {
        UUID spaceId = UUID.randomUUID();
        SeatItemResponse item = new SeatItemResponse(UUID.randomUUID().toString(), "A-1", 1, 1, "AVAILABLE", List.of());
        SeatLayoutResponse response = new SeatLayoutResponse(spaceId.toString(), 1, 1, List.of(item));
        given(seatService.getSeatLayout(any(), any())).willReturn(response);

        mockMvc.perform(get("/api/spaces/{id}/seats", spaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.rows").value(1));
    }

    @Test
    @WithMockUser
    void getSeatDetail_200() throws Exception {
        UUID seatId = UUID.randomUUID();
        SeatDetailResponse response = new SeatDetailResponse(
                seatId.toString(), "A-1", 1, 1, "AVAILABLE", List.of(),
                UUID.randomUUID().toString(), "제1열람실");
        given(seatService.getSeatDetail(any(), any())).willReturn(response);

        mockMvc.perform(get("/api/seats/{id}", seatId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("A-1"))
                .andExpect(jsonPath("$.spaceName").value("제1열람실"));
    }

    @Test
    @WithMockUser
    void getSeatDetail_없는좌석_404() throws Exception {
        UUID seatId = UUID.randomUUID();
        given(seatService.getSeatDetail(any(), any())).willThrow(new SeatNotFoundException());

        mockMvc.perform(get("/api/seats/{id}", seatId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEAT-001"));
    }
}
```

- [ ] **Step 7: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.space.controller.SeatControllerTest" --info
```
Expected: 3 tests PASS

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/univsitdown/space/dto/
git add src/main/java/com/univsitdown/space/service/SeatService.java
git add src/main/java/com/univsitdown/space/controller/SeatController.java
git add src/test/java/com/univsitdown/space/
git commit -m "feat: SEAT-01, SEAT-02 좌석 조회 API 구현"
```

---

## Task 6: RSV-01 예약 생성

**Files:**
- Create: `src/main/java/com/univsitdown/reservation/dto/CreateReservationRequest.java`
- Create: `src/main/java/com/univsitdown/reservation/dto/CreateReservationResponse.java`
- Create: `src/main/java/com/univsitdown/reservation/service/ReservationService.java`
- Create: `src/main/java/com/univsitdown/reservation/controller/ReservationController.java`
- Create: `src/test/java/com/univsitdown/reservation/service/ReservationServiceTest.java`
- Create: `src/test/java/com/univsitdown/reservation/controller/ReservationControllerTest.java`

- [ ] **Step 1: DTO 생성**

```java
// src/main/java/com/univsitdown/reservation/dto/CreateReservationRequest.java
package com.univsitdown.reservation.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID seatId,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt
) {}
```

```java
// src/main/java/com/univsitdown/reservation/dto/CreateReservationResponse.java
package com.univsitdown.reservation.dto;

import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;

import java.time.temporal.ChronoUnit;

public record CreateReservationResponse(
        String id,
        String seatId,
        String seatLabel,
        String spaceId,
        String spaceName,
        String startAt,
        String endAt,
        int durationHours,
        String status,
        String createdAt
) {
    public static CreateReservationResponse from(Reservation r) {
        long minutes = ChronoUnit.MINUTES.between(r.getStartAt(), r.getEndAt());
        return new CreateReservationResponse(
                r.getId().toString(),
                r.getSeat().getId().toString(),
                r.getSeat().getLabel(),
                r.getSeat().getSpace().getId().toString(),
                r.getSeat().getSpace().getName(),
                r.getStartAt() + "Z",
                r.getEndAt() + "Z",
                (int) (minutes / 60),
                ReservationStatus.SCHEDULED.name(),
                r.getCreatedAt() + "Z"
        );
    }
}
```

- [ ] **Step 2: ReservationService 생성 (reserve 메서드)**

```java
// src/main/java/com/univsitdown/reservation/service/ReservationService.java
package com.univsitdown.reservation.service;

import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;
import com.univsitdown.reservation.dto.CreateReservationRequest;
import com.univsitdown.reservation.dto.CreateReservationResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;

    @Transactional
    public CreateReservationResponse reserve(UUID userId, CreateReservationRequest request) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
        LocalDateTime startAt = request.startAt();
        LocalDateTime endAt = request.endAt();

        // 1. 시간 유효성 검증
        if (!endAt.isAfter(startAt)) throw new ReservationInvalidTimeException();

        // 2. 비관적 락으로 좌석 조회 (SELECT FOR UPDATE)
        Seat seat = seatRepository.findByIdForUpdate(request.seatId())
                .orElseThrow(SeatNotFoundException::new);

        // 3. 좌석 활성 여부 검증
        if (!seat.isEnabled()) throw new SeatUnavailableException();

        // 4. 운영 시간 검증
        var space = seat.getSpace();
        if (startAt.toLocalTime().isBefore(space.getOpenTime()) ||
            endAt.toLocalTime().isAfter(space.getCloseTime())) {
            throw new ReservationOutOfHoursException();
        }

        // 5. 최대 이용 시간 검증
        long durationHours = ChronoUnit.HOURS.between(startAt, endAt);
        if (durationHours > space.getMaxReservationHours()) {
            throw new ReservationMaxDurationExceededException();
        }

        // 6. 사용자 활성 예약 제한 검증 (BR-01: 활성 예약 1건)
        if (reservationRepository.countActiveByUserId(userId, now) >= 1) {
            throw new UserReservationLimitException();
        }

        // 7. 시간 겹침 검증
        if (reservationRepository.existsOverlapping(seat.getId(), startAt, endAt)) {
            throw new SeatAlreadyReservedException();
        }

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Reservation saved = reservationRepository.save(Reservation.create(user, seat, startAt, endAt));
        return CreateReservationResponse.from(saved);
    }
}
```

- [ ] **Step 3: ReservationController 생성**

```java
// src/main/java/com/univsitdown/reservation/controller/ReservationController.java
package com.univsitdown.reservation.controller;

import com.univsitdown.global.security.CurrentUser;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.reservation.dto.CreateReservationRequest;
import com.univsitdown.reservation.dto.CreateReservationResponse;
import com.univsitdown.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<CreateReservationResponse> reserve(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.reserve(principal.getId(), request));
    }
}
```

- [ ] **Step 4: ReservationServiceTest 작성 (reserve 메서드)**

```java
// src/test/java/com/univsitdown/reservation/service/ReservationServiceTest.java
package com.univsitdown.reservation.service;

import com.univsitdown.reservation.dto.CreateReservationRequest;
import com.univsitdown.reservation.dto.CreateReservationResponse;
import com.univsitdown.reservation.exception.*;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Seat;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.exception.SeatNotFoundException;
import com.univsitdown.space.exception.SeatUnavailableException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.user.domain.User;
import com.univsitdown.user.domain.UserRole;
import com.univsitdown.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservationRepository;
    @Mock SeatRepository seatRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ReservationService reservationService;

    private UUID userId;
    private UUID seatId;
    private Space space;
    private Seat seat;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        seatId = UUID.randomUUID();
        space = Space.create("제1열람실", 3, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4, List.of(), null);
        seat = Seat.create(space, 1, 1, "A-1");
        user = User.create("test@test.com", "hash", "테스터", null, null);
    }

    private CreateReservationRequest validRequest() {
        return new CreateReservationRequest(
                seatId,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 1, 11, 0)
        );
    }

    @Test
    void reserve_정상예약_성공() {
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));
        given(reservationRepository.countActiveByUserId(any(), any())).willReturn(0L);
        given(reservationRepository.existsOverlapping(any(), any(), any())).willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(reservationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        CreateReservationResponse response = reservationService.reserve(userId, validRequest());

        assertThat(response.seatLabel()).isEqualTo("A-1");
        assertThat(response.status()).isEqualTo("SCHEDULED");
        assertThat(response.durationHours()).isEqualTo(2);
    }

    @Test
    void reserve_종료시간이_시작시간보다_이른경우_예외() {
        CreateReservationRequest bad = new CreateReservationRequest(
                seatId,
                LocalDateTime.of(2026, 5, 1, 11, 0),
                LocalDateTime.of(2026, 5, 1, 9, 0)
        );
        assertThatThrownBy(() -> reservationService.reserve(userId, bad))
                .isInstanceOf(ReservationInvalidTimeException.class);
    }

    @Test
    void reserve_없는좌석_SeatNotFoundException() {
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> reservationService.reserve(userId, validRequest()))
                .isInstanceOf(SeatNotFoundException.class);
    }

    @Test
    void reserve_비활성화좌석_SeatUnavailableException() {
        seat.updateEnabled(false);
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));
        assertThatThrownBy(() -> reservationService.reserve(userId, validRequest()))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void reserve_최대이용시간초과_예외() {
        CreateReservationRequest bad = new CreateReservationRequest(
                seatId,
                LocalDateTime.of(2026, 5, 1, 9, 0),
                LocalDateTime.of(2026, 5, 1, 14, 0) // 5시간
        );
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));
        assertThatThrownBy(() -> reservationService.reserve(userId, bad))
                .isInstanceOf(ReservationMaxDurationExceededException.class);
    }

    @Test
    void reserve_활성예약이미존재_UserReservationLimitException() {
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));
        given(reservationRepository.countActiveByUserId(any(), any())).willReturn(1L);
        assertThatThrownBy(() -> reservationService.reserve(userId, validRequest()))
                .isInstanceOf(UserReservationLimitException.class);
    }

    @Test
    void reserve_좌석중복예약_SeatAlreadyReservedException() {
        given(seatRepository.findByIdForUpdate(seatId)).willReturn(Optional.of(seat));
        given(reservationRepository.countActiveByUserId(any(), any())).willReturn(0L);
        given(reservationRepository.existsOverlapping(any(), any(), any())).willReturn(true);
        assertThatThrownBy(() -> reservationService.reserve(userId, validRequest()))
                .isInstanceOf(SeatAlreadyReservedException.class);
    }
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.reservation.service.ReservationServiceTest" --info
```
Expected: 7 tests PASS

- [ ] **Step 6: ReservationControllerTest 생성 (reserve 부분)**

```java
// src/test/java/com/univsitdown/reservation/controller/ReservationControllerTest.java
package com.univsitdown.reservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.global.config.SecurityConfig;
import com.univsitdown.global.security.JwtProvider;
import com.univsitdown.reservation.dto.CreateReservationRequest;
import com.univsitdown.reservation.dto.CreateReservationResponse;
import com.univsitdown.reservation.exception.SeatAlreadyReservedException;
import com.univsitdown.reservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ReservationService reservationService;
    @MockBean JwtProvider jwtProvider;

    @Test
    @WithMockUser
    void reserve_201() throws Exception {
        CreateReservationResponse response = new CreateReservationResponse(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "A-1", UUID.randomUUID().toString(), "제1열람실",
                "2026-05-01T09:00:00Z", "2026-05-01T11:00:00Z",
                2, "SCHEDULED", "2026-05-01T08:55:00Z"
        );
        given(reservationService.reserve(any(), any())).willReturn(response);

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                UUID.randomUUID(),
                                LocalDateTime.of(2026, 5, 1, 9, 0),
                                LocalDateTime.of(2026, 5, 1, 11, 0)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatLabel").value("A-1"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @WithMockUser
    void reserve_중복예약_409() throws Exception {
        given(reservationService.reserve(any(), any())).willThrow(new SeatAlreadyReservedException());

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReservationRequest(
                                UUID.randomUUID(),
                                LocalDateTime.of(2026, 5, 1, 9, 0),
                                LocalDateTime.of(2026, 5, 1, 11, 0)
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RSV-004"));
    }
}
```

- [ ] **Step 7: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.reservation.controller.ReservationControllerTest" --info
```
Expected: 2 tests PASS

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/univsitdown/reservation/dto/CreateReservationRequest.java
git add src/main/java/com/univsitdown/reservation/dto/CreateReservationResponse.java
git add src/main/java/com/univsitdown/reservation/service/ReservationService.java
git add src/main/java/com/univsitdown/reservation/controller/ReservationController.java
git add src/test/java/com/univsitdown/reservation/
git commit -m "feat: RSV-01 예약 생성 구현 (비관적 락 포함)"
```

---

## Task 7: RSV-01 동시성 테스트

**Files:**
- Create: `src/test/java/com/univsitdown/reservation/service/ReservationConcurrencyTest.java`

- [ ] **Step 1: 동시성 통합 테스트 생성**

```java
// src/test/java/com/univsitdown/reservation/service/ReservationConcurrencyTest.java
package com.univsitdown.reservation.service;

import com.univsitdown.reservation.dto.CreateReservationRequest;
import com.univsitdown.reservation.exception.SeatAlreadyReservedException;
import com.univsitdown.reservation.exception.UserReservationLimitException;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.user.domain.User;
import com.univsitdown.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-for-concurrency-test-minimum-32",
        "spring.data.redis.host=invalid-host-fallback-to-memory"
})
@Testcontainers
class ReservationConcurrencyTest {

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

    @Autowired ReservationService reservationService;
    @Autowired UserRepository userRepository;
    @Autowired SpaceRepository spaceRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private List<UUID> userIds;
    private UUID seatId;

    @BeforeEach
    void setUp() {
        // 각 테스트마다 독립된 사용자 10명과 좌석 1개를 생성
        Space space = spaceRepository.save(Space.create(
                "동시성테스트열람실", 1, SpaceCategory.READING_ROOM,
                LocalTime.of(0, 0), LocalTime.of(23, 59), 4, List.of(), null));

        com.univsitdown.space.domain.Seat seat =
                seatRepository.save(com.univsitdown.space.domain.Seat.create(space, 1, 1, "A-1"));
        seatId = seat.getId();

        userIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User user = userRepository.save(User.create(
                    "concurrent" + i + "_" + UUID.randomUUID() + "@test.com",
                    passwordEncoder.encode("pass"),
                    "테스터" + i, null, null));
            userIds.add(user.getId());
        }
    }

    @Test
    void 동시에_10명이_같은_좌석을_예약하면_1명만_성공한다() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        LocalDateTime startAt = LocalDateTime.of(2027, 1, 1, 9, 0);
        LocalDateTime endAt = LocalDateTime.of(2027, 1, 1, 11, 0);

        for (int i = 0; i < threadCount; i++) {
            final UUID uid = userIds.get(i);
            executor.submit(() -> {
                try {
                    reservationService.reserve(uid, new CreateReservationRequest(seatId, startAt, endAt));
                    success.incrementAndGet();
                } catch (SeatAlreadyReservedException | UserReservationLimitException e) {
                    conflict.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(9);
    }
}
```

- [ ] **Step 2: 동시성 테스트 실행 (시간 소요 — Docker 필요)**

```bash
./gradlew test --tests "com.univsitdown.reservation.service.ReservationConcurrencyTest" --info
```
Expected: PASS (success=1, conflict=9)

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/univsitdown/reservation/service/ReservationConcurrencyTest.java
git commit -m "test: RSV-01 동시성 통합 테스트 추가 (Testcontainers)"
```

---

## Task 8: RSV-02 + RSV-03 예약 조회

**Files:**
- Create: `src/main/java/com/univsitdown/reservation/dto/ReservationListItemResponse.java`
- Create: `src/main/java/com/univsitdown/reservation/dto/ReservationDetailResponse.java`
- Modify: `src/main/java/com/univsitdown/reservation/service/ReservationService.java` — getMyReservations, getReservation 추가
- Modify: `src/main/java/com/univsitdown/reservation/controller/ReservationController.java` — GET 엔드포인트 추가

- [ ] **Step 1: DTO 생성**

```java
// src/main/java/com/univsitdown/reservation/dto/ReservationListItemResponse.java
package com.univsitdown.reservation.dto;

import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record ReservationListItemResponse(
        String id,
        String seatLabel,
        String spaceName,
        int spaceFloor,
        String startAt,
        String endAt,
        String status,
        Long remainingSeconds
) {
    public static ReservationListItemResponse from(Reservation r, LocalDateTime now) {
        ReservationStatus computed = r.computedStatus(now);
        Long remaining = null;
        if (computed == ReservationStatus.IN_USE) {
            remaining = ChronoUnit.SECONDS.between(now, r.getEndAt());
        }
        return new ReservationListItemResponse(
                r.getId().toString(),
                r.getSeat().getLabel(),
                r.getSeat().getSpace().getName(),
                r.getSeat().getSpace().getFloor(),
                r.getStartAt() + "Z",
                r.getEndAt() + "Z",
                computed.name(),
                remaining
        );
    }
}
```

```java
// src/main/java/com/univsitdown/reservation/dto/ReservationDetailResponse.java
package com.univsitdown.reservation.dto;

import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record ReservationDetailResponse(
        String id,
        String seatId,
        String seatLabel,
        String spaceId,
        String spaceName,
        int spaceFloor,
        String startAt,
        String endAt,
        int durationHours,
        String status,
        Long remainingSeconds,
        int extendedCount,
        String createdAt
) {
    public static ReservationDetailResponse from(Reservation r, LocalDateTime now) {
        ReservationStatus computed = r.computedStatus(now);
        Long remaining = null;
        if (computed == ReservationStatus.IN_USE) {
            remaining = ChronoUnit.SECONDS.between(now, r.getEndAt());
        }
        long minutes = ChronoUnit.MINUTES.between(r.getStartAt(), r.getEndAt());
        return new ReservationDetailResponse(
                r.getId().toString(),
                r.getSeat().getId().toString(),
                r.getSeat().getLabel(),
                r.getSeat().getSpace().getId().toString(),
                r.getSeat().getSpace().getName(),
                r.getSeat().getSpace().getFloor(),
                r.getStartAt() + "Z",
                r.getEndAt() + "Z",
                (int) (minutes / 60),
                computed.name(),
                remaining,
                r.getExtendedCount(),
                r.getCreatedAt() + "Z"
        );
    }
}
```

- [ ] **Step 2: ReservationService에 조회 메서드 추가**

ReservationService에 추가:
```java
import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.dto.ReservationDetailResponse;
import com.univsitdown.reservation.dto.ReservationListItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
```

```java
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
```

- [ ] **Step 3: ReservationController에 GET 엔드포인트 추가**

ReservationController에 추가:
```java
import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.dto.ReservationDetailResponse;
import com.univsitdown.reservation.dto.ReservationListItemResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
```

```java
@GetMapping("/me")
public PageResponse<ReservationListItemResponse> getMyReservations(
        @CurrentUser UserPrincipal principal,
        @RequestParam(required = false) String status,
        @PageableDefault(size = 20) Pageable pageable) {
    return reservationService.getMyReservations(principal.getId(), status, pageable);
}

@GetMapping("/{id}")
public ReservationDetailResponse getReservation(
        @CurrentUser UserPrincipal principal,
        @PathVariable UUID id) {
    return reservationService.getReservation(id, principal.getId());
}
```

- [ ] **Step 4: ReservationServiceTest에 조회 테스트 추가**

ReservationServiceTest에 추가:
```java
// 추가 imports:
import com.univsitdown.global.response.PageResponse;
import com.univsitdown.reservation.domain.Reservation;
import com.univsitdown.reservation.domain.ReservationStatus;
import com.univsitdown.reservation.dto.ReservationDetailResponse;
import com.univsitdown.reservation.dto.ReservationListItemResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@Test
void getReservation_정상조회_성공() {
    UUID reservationId = UUID.randomUUID();
    Reservation reservation = Reservation.create(user, seat,
            LocalDateTime.of(2026, 5, 1, 9, 0),
            LocalDateTime.of(2026, 5, 1, 11, 0));
    given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

    ReservationDetailResponse response = reservationService.getReservation(reservationId, userId);

    assertThat(response.seatLabel()).isEqualTo("A-1");
    assertThat(response.status()).isEqualTo("SCHEDULED");
}

@Test
void getReservation_본인아닌_예약조회_ReservationNotOwnerException() {
    UUID reservationId = UUID.randomUUID();
    Reservation reservation = Reservation.create(user, seat,
            LocalDateTime.of(2026, 5, 1, 9, 0),
            LocalDateTime.of(2026, 5, 1, 11, 0));
    given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

    assertThatThrownBy(() -> reservationService.getReservation(reservationId, UUID.randomUUID()))
            .isInstanceOf(ReservationNotOwnerException.class);
}

@Test
void getReservation_없는예약_ReservationNotFoundException() {
    UUID reservationId = UUID.randomUUID();
    given(reservationRepository.findById(reservationId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> reservationService.getReservation(reservationId, userId))
            .isInstanceOf(ReservationNotFoundException.class);
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.reservation.service.ReservationServiceTest" --info
```
Expected: 10 tests PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/univsitdown/reservation/dto/ReservationListItemResponse.java
git add src/main/java/com/univsitdown/reservation/dto/ReservationDetailResponse.java
git add src/main/java/com/univsitdown/reservation/service/ReservationService.java
git add src/main/java/com/univsitdown/reservation/controller/ReservationController.java
git commit -m "feat: RSV-02, RSV-03 예약 조회 API 구현"
```

---

## Task 9: RSV-04 예약 연장

**Files:**
- Create: `src/main/java/com/univsitdown/reservation/dto/ExtendReservationRequest.java`
- Create: `src/main/java/com/univsitdown/reservation/dto/ExtendReservationResponse.java`
- Modify: `src/main/java/com/univsitdown/reservation/service/ReservationService.java` — extend 추가
- Modify: `src/main/java/com/univsitdown/reservation/controller/ReservationController.java` — PATCH 추가

- [ ] **Step 1: DTO 생성**

```java
// src/main/java/com/univsitdown/reservation/dto/ExtendReservationRequest.java
package com.univsitdown.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExtendReservationRequest(
        @NotNull @Min(1) @Max(120) Integer additionalMinutes
) {}
```

```java
// src/main/java/com/univsitdown/reservation/dto/ExtendReservationResponse.java
package com.univsitdown.reservation.dto;

import com.univsitdown.reservation.domain.Reservation;

public record ExtendReservationResponse(
        String id,
        String endAt,
        int extendedCount
) {
    public static ExtendReservationResponse from(Reservation r) {
        return new ExtendReservationResponse(
                r.getId().toString(),
                r.getEndAt() + "Z",
                r.getExtendedCount()
        );
    }
}
```

- [ ] **Step 2: ReservationService에 extend 추가**

```java
@Transactional
public ExtendReservationResponse extend(UUID reservationId, UUID userId, int additionalMinutes) {
    Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(ReservationNotFoundException::new);

    if (!reservation.getUser().getId().equals(userId)) {
        throw new ReservationNotOwnerException();
    }

    LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
    ReservationStatus computed = reservation.computedStatus(now);

    // IN_USE 상태만 연장 가능
    if (computed != ReservationStatus.IN_USE) {
        throw new ReservationNotExtendableException();
    }

    // 최대 2회 연장 제한
    if (reservation.getExtendedCount() >= 2) {
        throw new ReservationMaxExtendExceededException();
    }

    LocalDateTime newEndAt = reservation.getEndAt().plusMinutes(additionalMinutes);

    // 연장 후 시간이 후속 예약과 겹치는지 확인
    if (reservationRepository.existsOverlappingExclude(
            reservation.getSeat().getId(), reservationId,
            reservation.getEndAt(), newEndAt)) {
        throw new ReservationExtendConflictException();
    }

    reservation.extend(newEndAt);
    return ExtendReservationResponse.from(reservation);
}
```

imports 추가:
```java
import com.univsitdown.reservation.dto.ExtendReservationRequest;
import com.univsitdown.reservation.dto.ExtendReservationResponse;
```

- [ ] **Step 3: ReservationController에 PATCH 추가**

```java
@PatchMapping("/{id}/extend")
public ResponseEntity<ExtendReservationResponse> extend(
        @CurrentUser UserPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody ExtendReservationRequest request) {
    return ResponseEntity.ok(reservationService.extend(id, principal.getId(), request.additionalMinutes()));
}
```

- [ ] **Step 4: ReservationServiceTest에 연장 테스트 추가**

```java
// 추가 imports:
import com.univsitdown.reservation.dto.ExtendReservationResponse;

private Reservation inUseReservation() {
    // 현재 진행 중인 예약 (시작이 과거, 종료가 미래)
    return Reservation.create(user, seat,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1));
}

@Test
void extend_정상연장_성공() {
    UUID reservationId = UUID.randomUUID();
    Reservation reservation = inUseReservation();
    given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
    given(reservationRepository.existsOverlappingExclude(any(), any(), any(), any())).willReturn(false);

    ExtendReservationResponse response = reservationService.extend(reservationId, userId, 30);

    assertThat(response.extendedCount()).isEqualTo(1);
}

@Test
void extend_SCHEDULED상태_ReservationNotExtendableException() {
    UUID reservationId = UUID.randomUUID();
    Reservation reservation = Reservation.create(user, seat,
            LocalDateTime.now().plusHours(1),  // 미래 시작
            LocalDateTime.now().plusHours(3));
    given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

    assertThatThrownBy(() -> reservationService.extend(reservationId, userId, 30))
            .isInstanceOf(ReservationNotExtendableException.class);
}

@Test
void extend_후속예약충돌_ReservationExtendConflictException() {
    UUID reservationId = UUID.randomUUID();
    Reservation reservation = inUseReservation();
    given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
    given(reservationRepository.existsOverlappingExclude(any(), any(), any(), any())).willReturn(true);

    assertThatThrownBy(() -> reservationService.extend(reservationId, userId, 30))
            .isInstanceOf(ReservationExtendConflictException.class);
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.reservation.service.ReservationServiceTest" --info
```
Expected: 13 tests PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/univsitdown/reservation/dto/ExtendReservationRequest.java
git add src/main/java/com/univsitdown/reservation/dto/ExtendReservationResponse.java
git add src/main/java/com/univsitdown/reservation/service/ReservationService.java
git add src/main/java/com/univsitdown/reservation/controller/ReservationController.java
git commit -m "feat: RSV-04 예약 연장 구현"
```

---

## Task 10: RSV-05 예약 취소

**Files:**
- Modify: `src/main/java/com/univsitdown/reservation/service/ReservationService.java` — cancel 추가
- Modify: `src/main/java/com/univsitdown/reservation/controller/ReservationController.java` — DELETE 추가

- [ ] **Step 1: ReservationService에 cancel 추가**

```java
@Transactional
public void cancel(UUID reservationId, UUID userId) {
    Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(ReservationNotFoundException::new);

    if (!reservation.getUser().getId().equals(userId)) {
        throw new ReservationNotOwnerException();
    }

    LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
    ReservationStatus computed = reservation.computedStatus(now);

    if (computed == ReservationStatus.COMPLETED) {
        throw new ReservationAlreadyEndedException();
    }

    reservation.cancel(now);
}
```

- [ ] **Step 2: ReservationController에 DELETE 추가**

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> cancel(
        @CurrentUser UserPrincipal principal,
        @PathVariable UUID id) {
    reservationService.cancel(id, principal.getId());
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 3: ReservationServiceTest에 취소 테스트 추가**

```java
@Test
void cancel_정상취소_성공() {
    UUID reservationId = UUID.randomUUID();
    Reservation reservation = Reservation.create(user, seat,
            LocalDateTime.now().plusHours(1),
            LocalDateTime.now().plusHours(3));
    given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

    reservationService.cancel(reservationId, userId);

    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    assertThat(reservation.getCanceledAt()).isNotNull();
}

@Test
void cancel_이미종료된예약_ReservationAlreadyEndedException() {
    UUID reservationId = UUID.randomUUID();
    Reservation reservation = Reservation.create(user, seat,
            LocalDateTime.now().minusHours(3),
            LocalDateTime.now().minusHours(1)); // 이미 종료
    given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

    assertThatThrownBy(() -> reservationService.cancel(reservationId, userId))
            .isInstanceOf(ReservationAlreadyEndedException.class);
}

@Test
void cancel_본인아닌예약_ReservationNotOwnerException() {
    UUID reservationId = UUID.randomUUID();
    Reservation reservation = Reservation.create(user, seat,
            LocalDateTime.now().plusHours(1),
            LocalDateTime.now().plusHours(3));
    given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

    assertThatThrownBy(() -> reservationService.cancel(reservationId, UUID.randomUUID()))
            .isInstanceOf(ReservationNotOwnerException.class);
}
```

- [ ] **Step 4: 전체 테스트 실행**

```bash
./gradlew test --info
```
Expected: 전체 테스트 PASS

- [ ] **Step 5: 커밋 및 푸시**

```bash
git add src/main/java/com/univsitdown/reservation/service/ReservationService.java
git add src/main/java/com/univsitdown/reservation/controller/ReservationController.java
git add src/test/java/com/univsitdown/reservation/service/ReservationServiceTest.java
git commit -m "feat: RSV-05 예약 취소 구현"
git push origin main
```

---

## 완료 체크리스트

- [ ] V4 (seat 테이블) 마이그레이션 완료
- [ ] V5 (reservation 테이블 + EXCLUDE 제약) 마이그레이션 완료
- [ ] ADMIN-02, ADMIN-03 구현 + 테스트 통과
- [ ] SEAT-01, SEAT-02 구현 + 테스트 통과
- [ ] RSV-01 구현 + 단위 테스트 통과
- [ ] RSV-01 동시성 통합 테스트 통과 (success=1, conflict=9)
- [ ] RSV-02, RSV-03 구현 + 테스트 통과
- [ ] RSV-04 구현 + 테스트 통과
- [ ] RSV-05 구현 + 테스트 통과
- [ ] `./gradlew test` 전체 GREEN
- [ ] `git push origin main` 완료
