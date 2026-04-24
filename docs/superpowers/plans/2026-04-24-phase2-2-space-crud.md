# Phase 2-2: Space CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 공간(Space) 도메인 CRUD API 3개를 구현한다 — 목록 조회, 상세 조회, 관리자 생성.

**Architecture:** SpaceController(조회)와 AdminSpaceController(생성)를 분리하고 SpaceService·SpaceRepository를 공유한다. PostgreSQL `text[]` 컬럼을 JPA `AttributeConverter`로 처리하며, 미구현 필드(totalSeats, congestion 등)는 stub 값으로 반환한다.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Data JPA, Flyway, JUnit 5, Mockito, MockMvc

---

## 파일 구조

```
src/main/java/com/univsitdown/
├── global/
│   └── converter/
│       └── StringListConverter.java          (신규) text[] ↔ List<String>
├── space/
│   ├── controller/
│   │   ├── SpaceController.java              (신규) GET /api/spaces, GET /api/spaces/{id}
│   │   └── AdminSpaceController.java         (신규) POST /api/admin/spaces
│   ├── domain/
│   │   ├── Space.java                        (신규) JPA Entity
│   │   └── SpaceCategory.java               (신규) Enum
│   ├── dto/
│   │   ├── SpaceListItemResponse.java        (신규) 목록 응답 Record
│   │   ├── SpaceDetailResponse.java          (신규) 상세/생성 응답 Record
│   │   └── CreateSpaceRequest.java           (신규) 생성 요청 Record
│   ├── exception/
│   │   └── SpaceNotFoundException.java       (신규) SPACE-001
│   ├── repository/
│   │   └── SpaceRepository.java              (신규) findByFilters JPQL
│   └── service/
│       └── SpaceService.java                 (신규) getSpaces, getSpace, createSpace
├── global/
│   └── config/
│       └── SecurityConfig.java               (수정) /api/spaces/**, /api/admin/** permitAll 추가

src/main/resources/db/migration/
└── V3__create_space_table.sql                (신규)

src/test/java/com/univsitdown/
└── space/
    ├── controller/
    │   ├── SpaceControllerTest.java          (신규)
    │   └── AdminSpaceControllerTest.java     (신규)
    └── service/
        └── SpaceServiceTest.java             (신규)
```

---

## Task 1: Flyway 마이그레이션 + StringListConverter

**Files:**
- Create: `src/main/resources/db/migration/V3__create_space_table.sql`
- Create: `src/main/java/com/univsitdown/global/converter/StringListConverter.java`

- [ ] **Step 1: Flyway 마이그레이션 파일 생성**

`src/main/resources/db/migration/V3__create_space_table.sql`
```sql
CREATE TABLE spaces (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(100) NOT NULL,
    floor                 INT          NOT NULL,
    category              VARCHAR(30)  NOT NULL,
    open_time             TIME         NOT NULL,
    close_time            TIME         NOT NULL,
    max_reservation_hours INT          NOT NULL DEFAULT 4,
    features              TEXT[]       NOT NULL DEFAULT '{}',
    thumbnail_url         VARCHAR(500)
);
```

- [ ] **Step 2: StringListConverter 작성**

`src/main/java/com/univsitdown/global/converter/StringListConverter.java`
```java
package com.univsitdown.global.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String[]> {

    @Override
    public String[] convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) return new String[0];
        return attribute.toArray(new String[0]);
    }

    @Override
    public List<String> convertToEntityAttribute(String[] dbData) {
        if (dbData == null || dbData.length == 0) return Collections.emptyList();
        return Arrays.asList(dbData);
    }
}
```

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/db/migration/V3__create_space_table.sql \
        src/main/java/com/univsitdown/global/converter/StringListConverter.java
git commit -m "feat: Space 테이블 Flyway 마이그레이션 및 StringListConverter 추가"
```

---

## Task 2: SpaceCategory Enum + Space Entity

**Files:**
- Create: `src/main/java/com/univsitdown/space/domain/SpaceCategory.java`
- Create: `src/main/java/com/univsitdown/space/domain/Space.java`

- [ ] **Step 1: SpaceCategory Enum 작성**

`src/main/java/com/univsitdown/space/domain/SpaceCategory.java`
```java
package com.univsitdown.space.domain;

public enum SpaceCategory {
    READING_ROOM, STUDY_ROOM, PC_ROOM, LECTURE_ROOM
}
```

- [ ] **Step 2: Space Entity 작성**

`src/main/java/com/univsitdown/space/domain/Space.java`
```java
package com.univsitdown.space.domain;

import com.univsitdown.global.converter.StringListConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "spaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int floor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SpaceCategory category;

    @Column(nullable = false)
    private LocalTime openTime;

    @Column(nullable = false)
    private LocalTime closeTime;

    @Column(nullable = false)
    private int maxReservationHours;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "text[]", nullable = false)
    private List<String> features;

    @Column(length = 500)
    private String thumbnailUrl;

    public static Space create(String name, int floor, SpaceCategory category,
                               LocalTime openTime, LocalTime closeTime,
                               int maxReservationHours, List<String> features,
                               String thumbnailUrl) {
        Space space = new Space();
        space.name = name;
        space.floor = floor;
        space.category = category;
        space.openTime = openTime;
        space.closeTime = closeTime;
        space.maxReservationHours = maxReservationHours;
        space.features = features != null ? features : List.of();
        space.thumbnailUrl = thumbnailUrl;
        return space;
    }
}
```

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/univsitdown/space/domain/
git commit -m "feat: SpaceCategory enum 및 Space Entity 추가"
```

---

## Task 3: SpaceRepository

**Files:**
- Create: `src/main/java/com/univsitdown/space/repository/SpaceRepository.java`

- [ ] **Step 1: SpaceRepository 작성**

`src/main/java/com/univsitdown/space/repository/SpaceRepository.java`
```java
package com.univsitdown.space.repository;

import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID> {

    @Query("""
            SELECT s FROM Space s
            WHERE (:category IS NULL OR s.category = :category)
              AND (:keyword IS NULL OR s.name LIKE %:keyword%)
            """)
    Page<Space> findByFilters(@Param("category") SpaceCategory category,
                              @Param("keyword") String keyword,
                              Pageable pageable);
}
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/univsitdown/space/repository/SpaceRepository.java
git commit -m "feat: SpaceRepository 추가 (카테고리·키워드 필터 JPQL)"
```

---

## Task 4: SpaceNotFoundException + DTO 3종

**Files:**
- Create: `src/main/java/com/univsitdown/space/exception/SpaceNotFoundException.java`
- Create: `src/main/java/com/univsitdown/space/dto/SpaceListItemResponse.java`
- Create: `src/main/java/com/univsitdown/space/dto/SpaceDetailResponse.java`
- Create: `src/main/java/com/univsitdown/space/dto/CreateSpaceRequest.java`

- [ ] **Step 1: SpaceNotFoundException 작성**

`src/main/java/com/univsitdown/space/exception/SpaceNotFoundException.java`
```java
package com.univsitdown.space.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class SpaceNotFoundException extends BusinessException {
    public SpaceNotFoundException() {
        super(ErrorCode.SPACE_NOT_FOUND);
    }
}
```

- [ ] **Step 2: SpaceListItemResponse 작성**

`src/main/java/com/univsitdown/space/dto/SpaceListItemResponse.java`
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
        return new SpaceListItemResponse(
                space.getId() != null ? space.getId().toString() : null,
                space.getName(),
                space.getFloor(),
                space.getCategory().name(),
                0,       // stub: Phase 2-3에서 Seat count 쿼리로 교체
                0,       // stub: Phase 2-3에서 예약 미존재 좌석 count로 교체
                "LOW",   // stub: Phase 5에서 Redis 집계값으로 교체
                space.getOpenTime().toString(),
                space.getCloseTime().toString(),
                space.getFeatures(),
                space.getThumbnailUrl()
        );
    }
}
```

- [ ] **Step 3: SpaceDetailResponse 작성**

`src/main/java/com/univsitdown/space/dto/SpaceDetailResponse.java`
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
        return new SpaceDetailResponse(
                space.getId() != null ? space.getId().toString() : null,
                space.getName(),
                space.getFloor(),
                space.getCategory().name(),
                0,          // stub: Phase 2-3
                0,          // stub: Phase 2-3
                0,          // stub: Phase 2-3
                0,          // stub: Phase 2-3
                "LOW",      // stub: Phase 5
                space.getOpenTime().toString(),
                space.getCloseTime().toString(),
                space.getMaxReservationHours(),
                space.getFeatures(),
                List.of(),  // stub: 미정 (S3 이미지)
                false       // stub: Phase 3 즐겨찾기
        );
    }
}
```

- [ ] **Step 4: CreateSpaceRequest 작성**

`src/main/java/com/univsitdown/space/dto/CreateSpaceRequest.java`
```java
package com.univsitdown.space.dto;

import com.univsitdown.space.domain.SpaceCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record CreateSpaceRequest(
        @NotBlank String name,
        @Min(1) int floor,
        @NotNull SpaceCategory category,
        @NotNull LocalTime openTime,
        @NotNull LocalTime closeTime,
        @Min(1) @Max(8) int maxReservationHours,
        List<String> features,
        String thumbnailUrl
) {}
```

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/univsitdown/space/exception/ \
        src/main/java/com/univsitdown/space/dto/
git commit -m "feat: Space 예외 클래스 및 DTO 3종 추가"
```

---

## Task 5: SpaceService (TDD)

**Files:**
- Create: `src/test/java/com/univsitdown/space/service/SpaceServiceTest.java`
- Create: `src/main/java/com/univsitdown/space/service/SpaceService.java`

- [ ] **Step 1: 테스트 먼저 작성 (SpaceService 없이)**

`src/test/java/com/univsitdown/space/service/SpaceServiceTest.java`
```java
package com.univsitdown.space.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @InjectMocks
    private SpaceService spaceService;

    private Space sampleSpace() {
        return Space.create("제1열람실", 3, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4,
                List.of("콘센트", "조용함"), null);
    }

    @Test
    void getSpaces_필터없음_목록조회_성공() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(null, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));

        PageResponse<SpaceListItemResponse> response = spaceService.getSpaces(null, null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).name()).isEqualTo("제1열람실");
        assertThat(response.content().get(0).totalSeats()).isEqualTo(0);
        assertThat(response.content().get(0).congestion()).isEqualTo("LOW");
    }

    @Test
    void getSpaces_category_필터_repository에_전달됨() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(spaceRepository.findByFilters(SpaceCategory.READING_ROOM, null, pageable))
                .willReturn(new PageImpl<>(List.of(sampleSpace())));

        spaceService.getSpaces(SpaceCategory.READING_ROOM, null, pageable);

        then(spaceRepository).should().findByFilters(SpaceCategory.READING_ROOM, null, pageable);
    }

    @Test
    void getSpace_정상조회_성공() {
        UUID id = UUID.randomUUID();
        given(spaceRepository.findById(id)).willReturn(Optional.of(sampleSpace()));

        SpaceDetailResponse response = spaceService.getSpace(id);

        assertThat(response.name()).isEqualTo("제1열람실");
        assertThat(response.isFavorite()).isFalse();
        assertThat(response.rows()).isEqualTo(0);
    }

    @Test
    void getSpace_없는ID_SpaceNotFoundException() {
        UUID id = UUID.randomUUID();
        given(spaceRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> spaceService.getSpace(id))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void createSpace_정상생성_성공() {
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
        assertThat(response.category()).isEqualTo("READING_ROOM");
        then(spaceRepository).should().save(any(Space.class));
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 에러 확인 (SpaceService 없음)**

```bash
./gradlew test --tests "com.univsitdown.space.service.SpaceServiceTest"
```
Expected: 컴파일 에러 (`SpaceService` 클래스 없음)

- [ ] **Step 3: SpaceService 구현**

`src/main/java/com/univsitdown/space/service/SpaceService.java`
```java
package com.univsitdown.space.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;

    @Transactional(readOnly = true)
    public PageResponse<SpaceListItemResponse> getSpaces(SpaceCategory category, String keyword, Pageable pageable) {
        Page<SpaceListItemResponse> page = spaceRepository
                .findByFilters(category, keyword, pageable)
                .map(SpaceListItemResponse::from);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public SpaceDetailResponse getSpace(UUID id) {
        Space space = spaceRepository.findById(id)
                .orElseThrow(SpaceNotFoundException::new);
        return SpaceDetailResponse.from(space);
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
}
```

- [ ] **Step 4: 테스트 재실행 — 전체 통과 확인**

```bash
./gradlew test --tests "com.univsitdown.space.service.SpaceServiceTest"
```
Expected: `BUILD SUCCESSFUL`, 5개 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/univsitdown/space/service/SpaceService.java \
        src/test/java/com/univsitdown/space/service/SpaceServiceTest.java
git commit -m "feat: SpaceService 구현 및 단위 테스트 추가"
```

---

## Task 6: SpaceController + SecurityConfig 수정

**Files:**
- Modify: `src/main/java/com/univsitdown/global/config/SecurityConfig.java`
- Create: `src/main/java/com/univsitdown/space/controller/SpaceController.java`
- Create: `src/test/java/com/univsitdown/space/controller/SpaceControllerTest.java`

- [ ] **Step 1: 테스트 먼저 작성**

`src/test/java/com/univsitdown/space/controller/SpaceControllerTest.java`
```java
package com.univsitdown.space.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.service.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SpaceController.class)
class SpaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpaceService spaceService;

    private static final SpaceListItemResponse SAMPLE_LIST_ITEM = new SpaceListItemResponse(
            UUID.randomUUID().toString(), "제1열람실", 3, "READING_ROOM",
            0, 0, "LOW", "06:00", "22:00",
            List.of("콘센트"), null
    );

    private static final SpaceDetailResponse SAMPLE_DETAIL = new SpaceDetailResponse(
            UUID.randomUUID().toString(), "제1열람실", 3, "READING_ROOM",
            0, 0, 0, 0, "LOW", "06:00", "22:00",
            4, List.of("콘센트"), List.of(), false
    );

    @Test
    @WithMockUser
    void getSpaces_200() throws Exception {
        given(spaceService.getSpaces(any(), any(), any()))
                .willReturn(new PageResponse<>(List.of(SAMPLE_LIST_ITEM), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("제1열람실"))
                .andExpect(jsonPath("$.content[0].congestion").value("LOW"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void getSpace_200() throws Exception {
        UUID id = UUID.randomUUID();
        given(spaceService.getSpace(id)).willReturn(SAMPLE_DETAIL);

        mockMvc.perform(get("/api/spaces/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("제1열람실"))
                .andExpect(jsonPath("$.isFavorite").value(false));
    }

    @Test
    @WithMockUser
    void getSpace_없는ID_404() throws Exception {
        UUID id = UUID.randomUUID();
        given(spaceService.getSpace(id)).willThrow(new SpaceNotFoundException());

        mockMvc.perform(get("/api/spaces/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPACE-001"));
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 에러 확인**

```bash
./gradlew test --tests "com.univsitdown.space.controller.SpaceControllerTest"
```
Expected: 컴파일 에러 (`SpaceController` 없음)

- [ ] **Step 3: SpaceController 구현**

`src/main/java/com/univsitdown/space/controller/SpaceController.java`
```java
package com.univsitdown.space.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping
    public ResponseEntity<PageResponse<SpaceListItemResponse>> getSpaces(
            @RequestParam(required = false) SpaceCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                spaceService.getSpaces(category, keyword, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpaceDetailResponse> getSpace(@PathVariable UUID id) {
        return ResponseEntity.ok(spaceService.getSpace(id));
    }
}
```

- [ ] **Step 4: SecurityConfig에 /api/spaces/** 추가**

`src/main/java/com/univsitdown/global/config/SecurityConfig.java` 수정:
```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(
                "/api/health",
                "/api/users/**",      // TODO: Phase 3에서 제거 — JWT @AuthenticationPrincipal로 교체
                "/api/spaces/**",     // TODO: Phase 3에서 제거 — JWT로 교체
                "/api/admin/**",      // TODO: Phase 3에서 ADMIN role 체크로 교체
                "/swagger-ui/**",
                "/api-docs/**"
        ).permitAll()
        .anyRequest().authenticated()
)
```

- [ ] **Step 5: 테스트 재실행 — 전체 통과 확인**

```bash
./gradlew test --tests "com.univsitdown.space.controller.SpaceControllerTest"
```
Expected: `BUILD SUCCESSFUL`, 3개 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/univsitdown/space/controller/SpaceController.java \
        src/main/java/com/univsitdown/global/config/SecurityConfig.java \
        src/test/java/com/univsitdown/space/controller/SpaceControllerTest.java
git commit -m "feat: SpaceController 구현 및 WebMvc 테스트 추가"
```

---

## Task 7: AdminSpaceController

**Files:**
- Create: `src/main/java/com/univsitdown/space/controller/AdminSpaceController.java`
- Create: `src/test/java/com/univsitdown/space/controller/AdminSpaceControllerTest.java`

- [ ] **Step 1: 테스트 먼저 작성**

`src/test/java/com/univsitdown/space/controller/AdminSpaceControllerTest.java`
```java
package com.univsitdown.space.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.space.domain.SpaceCategory;
import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.service.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSpaceController.class)
class AdminSpaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpaceService spaceService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final SpaceDetailResponse SAMPLE_DETAIL = new SpaceDetailResponse(
            UUID.randomUUID().toString(), "제2열람실", 2, "READING_ROOM",
            0, 0, 0, 0, "LOW", "06:00", "22:00",
            4, List.of("와이파이"), List.of(), false
    );

    @Test
    @WithMockUser
    void createSpace_201() throws Exception {
        given(spaceService.createSpace(any())).willReturn(SAMPLE_DETAIL);

        CreateSpaceRequest request = new CreateSpaceRequest(
                "제2열람실", 2, SpaceCategory.READING_ROOM,
                LocalTime.of(6, 0), LocalTime.of(22, 0), 4,
                List.of("와이파이"), null
        );

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("제2열람실"));
    }

    @Test
    @WithMockUser
    void createSpace_name_누락_400() throws Exception {
        String requestJson = """
                {
                  "floor": 2,
                  "category": "READING_ROOM",
                  "openTime": "06:00:00",
                  "closeTime": "22:00:00",
                  "maxReservationHours": 4
                }
                """;

        mockMvc.perform(post("/api/admin/spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 에러 확인**

```bash
./gradlew test --tests "com.univsitdown.space.controller.AdminSpaceControllerTest"
```
Expected: 컴파일 에러 (`AdminSpaceController` 없음)

- [ ] **Step 3: AdminSpaceController 구현**

`src/main/java/com/univsitdown/space/controller/AdminSpaceController.java`
```java
package com.univsitdown.space.controller;

import com.univsitdown.space.dto.CreateSpaceRequest;
import com.univsitdown.space.dto.SpaceDetailResponse;
import com.univsitdown.space.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/spaces")
@RequiredArgsConstructor
public class AdminSpaceController {

    private final SpaceService spaceService;

    @PostMapping
    public ResponseEntity<SpaceDetailResponse> createSpace(
            @Valid @RequestBody CreateSpaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(spaceService.createSpace(request));
    }
}
```

- [ ] **Step 4: 테스트 재실행 — 전체 통과 확인**

```bash
./gradlew test --tests "com.univsitdown.space.controller.AdminSpaceControllerTest"
```
Expected: `BUILD SUCCESSFUL`, 2개 테스트 PASS

- [ ] **Step 5: 전체 테스트 실행**

```bash
./gradlew test
```
Expected: `BUILD SUCCESSFUL`, 모든 테스트 PASS (기존 User 테스트 포함)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/univsitdown/space/controller/AdminSpaceController.java \
        src/test/java/com/univsitdown/space/controller/AdminSpaceControllerTest.java
git commit -m "feat: AdminSpaceController 구현 및 WebMvc 테스트 추가"
```
