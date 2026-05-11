# Remaining APIs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 미구현 API 5종(NOTI-01/02 공지사항, SPACE-03 혼잡도 예측, SPACE-04/05 즐겨찾기, STAT-01 이용 통계, USER-03 프로필 사진 업로드)을 구현한다.

**Architecture:** 기존 레이어드 아키텍처(controller/service/repository/domain/dto/exception) 패턴을 그대로 따른다. `notice/`·`stat/` 패키지를 신규 생성하고, 즐겨찾기·혼잡도 예측은 기존 `space/`에, 프로필 사진은 기존 `user/`에 편입한다. DB 변경은 Flyway V7(notice), V8(user_favorites)로 관리한다.

**Tech Stack:** Java 17 Records(DTO), Lombok(Entity), Spring Data JPA, native query (stat 집계), Multipart file upload, Redis cache

---

## 파일 맵

| 구분 | 파일 |
|---|---|
| 생성 | `db/migration/V7__create_notice_table.sql` |
| 생성 | `db/migration/V8__create_user_favorites_table.sql` |
| 생성 | `notice/domain/NoticeCategory.java` |
| 생성 | `notice/domain/Notice.java` |
| 생성 | `notice/repository/NoticeRepository.java` |
| 생성 | `notice/dto/NoticeListItemResponse.java` |
| 생성 | `notice/dto/NoticeDetailResponse.java` |
| 생성 | `notice/exception/NoticeNotFoundException.java` |
| 생성 | `notice/service/NoticeService.java` |
| 생성 | `notice/controller/NoticeController.java` |
| 생성 | `space/domain/UserFavorite.java` |
| 생성 | `space/repository/UserFavoriteRepository.java` |
| 생성 | `space/service/FavoriteService.java` |
| 생성 | `stat/dto/StatResponse.java` |
| 생성 | `stat/service/StatService.java` |
| 생성 | `stat/controller/StatController.java` |
| 수정 | `global/exception/ErrorCode.java` (NOTI/STAT 에러코드 추가) |
| 수정 | `reservation/repository/ReservationRepository.java` (혼잡도·통계 쿼리 추가) |
| 수정 | `space/service/SpaceService.java` (혼잡도·즐겨찾기 연결) |
| 수정 | `space/controller/SpaceController.java` (SPACE-03/04/05 엔드포인트 추가) |
| 수정 | `space/dto/SpaceDetailResponse.java` (isFavorite 파라미터 추가) |
| 수정 | `user/domain/User.java` (updateProfileImageUrl 메서드 추가) |
| 수정 | `user/service/UserService.java` (updateProfileImage 메서드 추가) |
| 수정 | `user/controller/UserController.java` (USER-03 엔드포인트 추가) |
| 수정 | `global/config/CacheConfig.java` (congestion 캐시 추가) |
| 수정 | `application.yml` (app.upload-dir 설정 추가) |
| 수정 | `application-local.yml` (upload-dir 경로 지정) |

---

## Task 1: ErrorCode에 신규 에러 코드 추가

**Files:**
- Modify: `src/main/java/com/univsitdown/global/exception/ErrorCode.java`

- [ ] **Step 1: NOTI·STAT 에러 코드 추가**

`// ADMIN` 블록 바로 아래에 추가:

```java
// NOTICE
NOTICE_NOT_FOUND("NOTI-001", HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다."),

// STAT
STAT_INVALID_PERIOD("STAT-001", HttpStatus.BAD_REQUEST, "유효하지 않은 조회 기간입니다."),
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/java/com/univsitdown/global/exception/ErrorCode.java
git commit -m "feat: NOTI/STAT 에러 코드 추가"
```

---

## Task 2: Flyway V7 — notice 테이블 생성 + 샘플 데이터

**Files:**
- Create: `src/main/resources/db/migration/V7__create_notice_table.sql`

- [ ] **Step 1: 마이그레이션 파일 작성**

```sql
CREATE TABLE notices (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    category     VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    is_active    BOOLEAN      NOT NULL DEFAULT true,
    published_at TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at   TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_notices_category    ON notices(category);
CREATE INDEX idx_notices_published_at ON notices(published_at DESC);

-- 샘플 데이터 (개발/테스트용)
INSERT INTO notices (title, content, category, published_at) VALUES
('도서관 이용 안내', '열람실 이용 시 음식물 반입을 금지합니다.', 'INFO', now()),
('시스템 점검 안내', '5월 15일 오전 2시~4시 시스템 점검이 있습니다.', 'MAINTENANCE', now()),
('이벤트 안내', '도서관 주간 행사가 진행됩니다.', 'EVENT', now());
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/resources/db/migration/V7__create_notice_table.sql
git commit -m "feat: V7 notice 테이블 마이그레이션"
```

---

## Task 3: Notice 도메인 — enum, entity, repository, DTO, exception

**Files:**
- Create: `src/main/java/com/univsitdown/notice/domain/NoticeCategory.java`
- Create: `src/main/java/com/univsitdown/notice/domain/Notice.java`
- Create: `src/main/java/com/univsitdown/notice/repository/NoticeRepository.java`
- Create: `src/main/java/com/univsitdown/notice/dto/NoticeListItemResponse.java`
- Create: `src/main/java/com/univsitdown/notice/dto/NoticeDetailResponse.java`
- Create: `src/main/java/com/univsitdown/notice/exception/NoticeNotFoundException.java`

- [ ] **Step 1: NoticeCategory enum**

```java
package com.univsitdown.notice.domain;

public enum NoticeCategory {
    INFO, MAINTENANCE, EVENT
}
```

- [ ] **Step 2: Notice entity**

```java
package com.univsitdown.notice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoticeCategory category;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private Instant publishedAt;

    @Column
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
```

- [ ] **Step 3: NoticeRepository**

```java
package com.univsitdown.notice.repository;

import com.univsitdown.notice.domain.Notice;
import com.univsitdown.notice.domain.NoticeCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {

    @Query("""
            SELECT n FROM Notice n
            WHERE n.isActive = true
            AND (:category IS NULL OR n.category = :category)
            ORDER BY n.publishedAt DESC
            """)
    Page<Notice> findActiveByCategory(@Param("category") NoticeCategory category, Pageable pageable);
}
```

- [ ] **Step 4: DTOs**

```java
// NoticeListItemResponse.java
package com.univsitdown.notice.dto;

import com.univsitdown.notice.domain.Notice;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record NoticeListItemResponse(
        String id,
        String title,
        String category,
        String publishedAt,
        boolean isNew
) {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.ofHours(9));

    public static NoticeListItemResponse from(Notice notice) {
        boolean isNew = notice.getPublishedAt().isAfter(
                java.time.Instant.now().minusSeconds(86400 * 7));
        return new NoticeListItemResponse(
                notice.getId().toString(),
                notice.getTitle(),
                notice.getCategory().name(),
                FORMATTER.format(notice.getPublishedAt()),
                isNew
        );
    }
}
```

```java
// NoticeDetailResponse.java
package com.univsitdown.notice.dto;

import com.univsitdown.notice.domain.Notice;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record NoticeDetailResponse(
        String id,
        String title,
        String content,
        String category,
        String publishedAt
) {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.ofHours(9));

    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(
                notice.getId().toString(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCategory().name(),
                FORMATTER.format(notice.getPublishedAt())
        );
    }
}
```

- [ ] **Step 5: NoticeNotFoundException**

```java
package com.univsitdown.notice.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class NoticeNotFoundException extends BusinessException {
    public NoticeNotFoundException() {
        super(ErrorCode.NOTICE_NOT_FOUND);
    }
}
```

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/univsitdown/notice/
git commit -m "feat: Notice 도메인 클래스 추가 (entity, repository, dto, exception)"
```

---

## Task 4: NoticeService + NoticeController (NOTI-01, NOTI-02)

**Files:**
- Create: `src/main/java/com/univsitdown/notice/service/NoticeService.java`
- Create: `src/main/java/com/univsitdown/notice/controller/NoticeController.java`

- [ ] **Step 1: NoticeService 작성**

```java
package com.univsitdown.notice.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.notice.domain.NoticeCategory;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.exception.NoticeNotFoundException;
import com.univsitdown.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    @Transactional(readOnly = true)
    public PageResponse<NoticeListItemResponse> getNotices(String categoryParam, Pageable pageable) {
        NoticeCategory category = (categoryParam == null || categoryParam.equalsIgnoreCase("ALL"))
                ? null
                : NoticeCategory.valueOf(categoryParam);
        return PageResponse.from(
                noticeRepository.findActiveByCategory(category, pageable)
                        .map(NoticeListItemResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse getNotice(UUID id) {
        return noticeRepository.findById(id)
                .filter(n -> n.isActive())
                .map(NoticeDetailResponse::from)
                .orElseThrow(NoticeNotFoundException::new);
    }
}
```

- [ ] **Step 2: NoticeController 작성**

```java
package com.univsitdown.notice.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public PageResponse<NoticeListItemResponse> getNotices(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noticeService.getNotices(category, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public NoticeDetailResponse getNotice(@PathVariable UUID id) {
        return noticeService.getNotice(id);
    }
}
```

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/univsitdown/notice/service/ \
        src/main/java/com/univsitdown/notice/controller/
git commit -m "feat: NOTI-01/02 공지사항 목록·상세 조회 API 구현"
```

---

## Task 5: Notice 테스트

**Files:**
- Create: `src/test/java/com/univsitdown/notice/service/NoticeServiceTest.java`
- Create: `src/test/java/com/univsitdown/notice/controller/NoticeControllerTest.java`

- [ ] **Step 1: NoticeServiceTest 작성**

```java
package com.univsitdown.notice.service;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.notice.domain.Notice;
import com.univsitdown.notice.domain.NoticeCategory;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.exception.NoticeNotFoundException;
import com.univsitdown.notice.repository.NoticeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock NoticeRepository noticeRepository;
    @InjectMocks NoticeService noticeService;

    @Test
    void getNotices_카테고리없음_전체반환() {
        given(noticeRepository.findActiveByCategory(isNull(), any()))
                .willReturn(new PageImpl<>(List.of()));
        PageResponse<NoticeListItemResponse> result = noticeService.getNotices(null, PageRequest.of(0, 20));
        assertThat(result.content()).isEmpty();
    }

    @Test
    void getNotices_ALL_카테고리_전체반환() {
        given(noticeRepository.findActiveByCategory(isNull(), any()))
                .willReturn(new PageImpl<>(List.of()));
        PageResponse<NoticeListItemResponse> result = noticeService.getNotices("ALL", PageRequest.of(0, 20));
        assertThat(result.content()).isEmpty();
    }

    @Test
    void getNotice_존재하면_반환() {
        Notice notice = createNotice();
        given(noticeRepository.findById(notice.getId())).willReturn(Optional.of(notice));
        NoticeDetailResponse response = noticeService.getNotice(notice.getId());
        assertThat(response.title()).isEqualTo("테스트 공지");
    }

    @Test
    void getNotice_없으면_예외() {
        given(noticeRepository.findById(any())).willReturn(Optional.empty());
        assertThatThrownBy(() -> noticeService.getNotice(UUID.randomUUID()))
                .isInstanceOf(NoticeNotFoundException.class);
    }

    private Notice createNotice() {
        // 리플렉션으로 Notice 생성 (protected constructor 우회)
        try {
            var constructor = Notice.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Notice notice = constructor.newInstance();
            var id = Notice.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(notice, UUID.randomUUID());
            var title = Notice.class.getDeclaredField("title");
            title.setAccessible(true);
            title.set(notice, "테스트 공지");
            var content = Notice.class.getDeclaredField("content");
            content.setAccessible(true);
            content.set(notice, "테스트 내용");
            var category = Notice.class.getDeclaredField("category");
            category.setAccessible(true);
            category.set(notice, NoticeCategory.INFO);
            var isActive = Notice.class.getDeclaredField("isActive");
            isActive.setAccessible(true);
            isActive.set(notice, true);
            var publishedAt = Notice.class.getDeclaredField("publishedAt");
            publishedAt.setAccessible(true);
            publishedAt.set(notice, java.time.Instant.now());
            var createdAt = Notice.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(notice, java.time.Instant.now());
            return notice;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: NoticeControllerTest 작성**

```java
package com.univsitdown.notice.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.notice.dto.NoticeDetailResponse;
import com.univsitdown.notice.dto.NoticeListItemResponse;
import com.univsitdown.notice.exception.NoticeNotFoundException;
import com.univsitdown.notice.service.NoticeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoticeController.class)
class NoticeControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean NoticeService noticeService;

    @Test
    @WithMockUser
    void getNotices_200() throws Exception {
        given(noticeService.getNotices(any(), any()))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, false));
        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser
    void getNotice_없으면_404() throws Exception {
        given(noticeService.getNotice(any())).willThrow(new NoticeNotFoundException());
        mockMvc.perform(get("/api/notices/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTI-001"));
    }
}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.notice.*" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/com/univsitdown/notice/
git commit -m "test: Notice 서비스·컨트롤러 테스트 추가"
```

---

## Task 6: Flyway V8 — user_favorites 테이블

**Files:**
- Create: `src/main/resources/db/migration/V8__create_user_favorites_table.sql`

- [ ] **Step 1: 마이그레이션 파일 작성**

```sql
CREATE TABLE user_favorites (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID      NOT NULL,
    space_id   UUID      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_space UNIQUE (user_id, space_id)
);

CREATE INDEX idx_user_favorites_user_id ON user_favorites(user_id);
```

- [ ] **Step 2: 커밋**

```bash
git add src/main/resources/db/migration/V8__create_user_favorites_table.sql
git commit -m "feat: V8 user_favorites 테이블 마이그레이션"
```

---

## Task 7: UserFavorite 엔티티 + Repository + FavoriteService

**Files:**
- Create: `src/main/java/com/univsitdown/space/domain/UserFavorite.java`
- Create: `src/main/java/com/univsitdown/space/repository/UserFavoriteRepository.java`
- Create: `src/main/java/com/univsitdown/space/service/FavoriteService.java`

- [ ] **Step 1: UserFavorite entity**

```java
package com.univsitdown.space.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_favorites",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "space_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID spaceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static UserFavorite of(UUID userId, UUID spaceId) {
        UserFavorite fav = new UserFavorite();
        fav.userId = userId;
        fav.spaceId = spaceId;
        fav.createdAt = Instant.now();
        return fav;
    }
}
```

- [ ] **Step 2: UserFavoriteRepository**

```java
package com.univsitdown.space.repository;

import com.univsitdown.space.domain.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UUID> {

    boolean existsByUserIdAndSpaceId(UUID userId, UUID spaceId);

    Optional<UserFavorite> findByUserIdAndSpaceId(UUID userId, UUID spaceId);
}
```

- [ ] **Step 3: FavoriteService**

```java
package com.univsitdown.space.service;

import com.univsitdown.space.domain.UserFavorite;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.space.repository.UserFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteRepository userFavoriteRepository;
    private final SpaceRepository spaceRepository;

    @Transactional
    public void addFavorite(UUID userId, UUID spaceId) {
        if (!spaceRepository.existsById(spaceId)) {
            throw new SpaceNotFoundException();
        }
        if (userFavoriteRepository.existsByUserIdAndSpaceId(userId, spaceId)) {
            return; // 이미 즐겨찾기 — 멱등 처리
        }
        userFavoriteRepository.save(UserFavorite.of(userId, spaceId));
    }

    @Transactional
    public void removeFavorite(UUID userId, UUID spaceId) {
        userFavoriteRepository.findByUserIdAndSpaceId(userId, spaceId)
                .ifPresent(userFavoriteRepository::delete);
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(UUID userId, UUID spaceId) {
        return userFavoriteRepository.existsByUserIdAndSpaceId(userId, spaceId);
    }
}
```

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/univsitdown/space/domain/UserFavorite.java \
        src/main/java/com/univsitdown/space/repository/UserFavoriteRepository.java \
        src/main/java/com/univsitdown/space/service/FavoriteService.java
git commit -m "feat: UserFavorite 엔티티·레포·서비스 추가"
```

---

## Task 8: SpaceController에 즐겨찾기 엔드포인트 + SpaceDetailResponse isFavorite 연결

**Files:**
- Modify: `src/main/java/com/univsitdown/space/dto/SpaceDetailResponse.java`
- Modify: `src/main/java/com/univsitdown/space/service/SpaceService.java`
- Modify: `src/main/java/com/univsitdown/space/controller/SpaceController.java`

- [ ] **Step 1: SpaceDetailResponse — isFavorite를 파라미터로 받도록 수정**

`from(Space space, int totalSeats, int availableSeats)` 메서드를 다음으로 교체:

```java
public static SpaceDetailResponse from(Space space, int totalSeats, int availableSeats, boolean isFavorite) {
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
            isFavorite
    );
}

public static SpaceDetailResponse from(Space space, int totalSeats, int availableSeats) {
    return from(space, totalSeats, availableSeats, false);
}

public static SpaceDetailResponse from(Space space) {
    return from(space, 0, 0, false);
}
```

- [ ] **Step 2: SpaceService — getSpace에 userId 파라미터 추가 + FavoriteService 주입**

`SpaceService`에 `FavoriteService` 필드 추가 후 `getSpace` 수정:

```java
private final FavoriteService favoriteService;

@Transactional(readOnly = true)
@Cacheable(value = "space:detail", key = "#id + ':' + #userId")
public SpaceDetailResponse getSpace(UUID id, UUID userId) {
    Space space = spaceRepository.findById(id)
            .orElseThrow(SpaceNotFoundException::new);
    LocalDateTime now = LocalDateTime.now(ZoneOffset.ofHours(9));
    int total = (int) seatRepository.countBySpaceIdAndIsEnabledTrue(id);
    int occupied = (int) reservationRepository.countOccupiedBySpaceId(id, now);
    boolean isFav = userId != null && favoriteService.isFavorite(userId, id);
    return SpaceDetailResponse.from(space, total, total - occupied, isFav);
}
```

- [ ] **Step 3: SpaceController — getSpace, addFavorite, removeFavorite**

현재 `SpaceController`의 `getSpace` 메서드를 수정하고 즐겨찾기 엔드포인트 추가:

```java
// 기존 getSpace 수정
@GetMapping("/{id}")
public SpaceDetailResponse getSpace(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserPrincipal principal) {
    UUID userId = principal != null ? principal.getId() : null;
    return spaceService.getSpace(id, userId);
}

// SPACE-04: 즐겨찾기 추가
@PostMapping("/{id}/favorite")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void addFavorite(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserPrincipal principal) {
    favoriteService.addFavorite(principal.getId(), id);
}

// SPACE-05: 즐겨찾기 해제
@DeleteMapping("/{id}/favorite")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void removeFavorite(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserPrincipal principal) {
    favoriteService.removeFavorite(principal.getId(), id);
}
```

`SpaceController`에 `FavoriteService favoriteService` 필드와 `HttpStatus` import도 추가.

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/univsitdown/space/dto/SpaceDetailResponse.java \
        src/main/java/com/univsitdown/space/service/SpaceService.java \
        src/main/java/com/univsitdown/space/controller/SpaceController.java
git commit -m "feat: SPACE-04/05 즐겨찾기 추가·해제 API 구현, isFavorite 실제 연결"
```

---

## Task 9: 즐겨찾기 테스트

**Files:**
- Create: `src/test/java/com/univsitdown/space/service/FavoriteServiceTest.java`

- [ ] **Step 1: FavoriteServiceTest 작성**

```java
package com.univsitdown.space.service;

import com.univsitdown.space.domain.UserFavorite;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SpaceRepository;
import com.univsitdown.space.repository.UserFavoriteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock UserFavoriteRepository userFavoriteRepository;
    @Mock SpaceRepository spaceRepository;
    @InjectMocks FavoriteService favoriteService;

    @Test
    void addFavorite_공간없으면_예외() {
        given(spaceRepository.existsById(any())).willReturn(false);
        assertThatThrownBy(() -> favoriteService.addFavorite(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void addFavorite_이미존재하면_멱등처리() {
        given(spaceRepository.existsById(any())).willReturn(true);
        given(userFavoriteRepository.existsByUserIdAndSpaceId(any(), any())).willReturn(true);
        favoriteService.addFavorite(UUID.randomUUID(), UUID.randomUUID());
        verify(userFavoriteRepository, never()).save(any());
    }

    @Test
    void addFavorite_정상저장() {
        given(spaceRepository.existsById(any())).willReturn(true);
        given(userFavoriteRepository.existsByUserIdAndSpaceId(any(), any())).willReturn(false);
        given(userFavoriteRepository.save(any())).willReturn(mock(UserFavorite.class));
        favoriteService.addFavorite(UUID.randomUUID(), UUID.randomUUID());
        verify(userFavoriteRepository).save(any());
    }

    @Test
    void removeFavorite_없으면_무시() {
        given(userFavoriteRepository.findByUserIdAndSpaceId(any(), any())).willReturn(Optional.empty());
        favoriteService.removeFavorite(UUID.randomUUID(), UUID.randomUUID());
        verify(userFavoriteRepository, never()).delete(any());
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.space.service.FavoriteServiceTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/univsitdown/space/service/FavoriteServiceTest.java
git commit -m "test: FavoriteService 테스트 추가"
```

---

## Task 10: SPACE-03 혼잡도 예측 — 쿼리 + 서비스 + 컨트롤러

혼잡도 예측 로직: 조회 날짜와 **같은 요일**의 **최근 4주** 데이터에서 시간대별 평균 점유율 계산.

**Files:**
- Modify: `src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java`
- Modify: `src/main/java/com/univsitdown/space/service/SpaceService.java`
- Modify: `src/main/java/com/univsitdown/space/controller/SpaceController.java`
- Create: `src/main/java/com/univsitdown/space/dto/CongestionPredictionResponse.java`

- [ ] **Step 1: CongestionPredictionResponse DTO**

```java
package com.univsitdown.space.dto;

import java.util.List;

public record CongestionPredictionResponse(
        String spaceId,
        String date,
        List<HourlyItem> hourly
) {
    public record HourlyItem(int hour, double occupancyRate, String level) {}

    public static String toLevel(double rate) {
        if (rate < 0.40) return "LOW";
        if (rate < 0.75) return "NORMAL";
        return "HIGH";
    }
}
```

- [ ] **Step 2: ReservationRepository에 시간대별 점유 쿼리 추가**

기존 파일 끝에 추가:

```java
// SPACE-03: 특정 시간대(슬롯)에 공간에서 점유 중인 좌석 수
@Query("""
        SELECT COUNT(DISTINCT r.seat.id)
        FROM Reservation r
        WHERE r.seat.space.id = :spaceId
        AND r.status NOT IN ('CANCELED', 'NO_SHOW')
        AND r.startAt < :slotEnd AND r.endAt > :slotStart
        """)
long countOccupiedAtSlot(@Param("spaceId") UUID spaceId,
                          @Param("slotStart") LocalDateTime slotStart,
                          @Param("slotEnd") LocalDateTime slotEnd);
```

- [ ] **Step 3: SpaceService에 getCongestionPrediction 추가**

```java
@Transactional(readOnly = true)
@Cacheable(value = "space:congestion", key = "#id + ':' + #date")
public CongestionPredictionResponse getCongestionPrediction(UUID id, LocalDate date) {
    Space space = spaceRepository.findById(id)
            .orElseThrow(SpaceNotFoundException::new);
    int totalSeats = (int) seatRepository.countBySpaceIdAndIsEnabledTrue(id);

    int openHour = space.getOpenTime().getHour();
    int closeHour = space.getCloseTime().getHour();

    // 같은 요일 최근 4주 기준일
    List<LocalDate> refDates = java.util.stream.IntStream.rangeClosed(1, 4)
            .mapToObj(w -> date.minusWeeks(w))
            .toList();

    List<CongestionPredictionResponse.HourlyItem> hourly = new java.util.ArrayList<>();
    for (int h = openHour; h < closeHour; h++) {
        double sum = 0;
        for (LocalDate ref : refDates) {
            LocalDateTime slotStart = ref.atTime(h, 0);
            LocalDateTime slotEnd   = ref.atTime(h + 1, 0);
            long occupied = reservationRepository.countOccupiedAtSlot(id, slotStart, slotEnd);
            sum += totalSeats > 0 ? (double) occupied / totalSeats : 0.0;
        }
        double avgRate = sum / refDates.size();
        hourly.add(new CongestionPredictionResponse.HourlyItem(
                h, Math.round(avgRate * 100.0) / 100.0,
                CongestionPredictionResponse.toLevel(avgRate)));
    }
    return new CongestionPredictionResponse(id.toString(), date.toString(), hourly);
}
```

`SpaceService` import에 `java.time.LocalDate` 추가.

- [ ] **Step 4: SpaceController에 엔드포인트 추가**

```java
@GetMapping("/{id}/congestion")
public CongestionPredictionResponse getCongestion(
        @PathVariable UUID id,
        @RequestParam(required = false) String date) {
    LocalDate targetDate = date != null
            ? LocalDate.parse(date)
            : LocalDate.now(ZoneOffset.ofHours(9));
    return spaceService.getCongestionPrediction(id, targetDate);
}
```

`SpaceController` import에 `java.time.LocalDate`, `java.time.ZoneOffset` 추가.

- [ ] **Step 5: CacheConfig에 congestion 캐시 추가**

`CacheConfig`의 `RedisCacheManager.builder(...)` 체이닝에 추가:

```java
.withCacheConfiguration("space:congestion", base.entryTtl(Duration.ofMinutes(10)))
```

`ConcurrentMapCacheManager` 생성자 인수에도 `"space:congestion"` 추가.

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/univsitdown/space/dto/CongestionPredictionResponse.java \
        src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java \
        src/main/java/com/univsitdown/space/service/SpaceService.java \
        src/main/java/com/univsitdown/space/controller/SpaceController.java \
        src/main/java/com/univsitdown/global/config/CacheConfig.java
git commit -m "feat: SPACE-03 혼잡도 예측 API 구현 (과거 4주 평균)"
```

---

## Task 11: 혼잡도 예측 테스트

**Files:**
- Create: `src/test/java/com/univsitdown/space/service/CongestionPredictionTest.java`

- [ ] **Step 1: 테스트 작성**

```java
package com.univsitdown.space.service;

import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.space.domain.Space;
import com.univsitdown.space.dto.CongestionPredictionResponse;
import com.univsitdown.space.exception.SpaceNotFoundException;
import com.univsitdown.space.repository.SeatRepository;
import com.univsitdown.space.repository.SpaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CongestionPredictionTest {

    @Mock SpaceRepository spaceRepository;
    @Mock SeatRepository seatRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock FavoriteService favoriteService;
    @InjectMocks SpaceService spaceService;

    @Test
    void getCongestionPrediction_공간없으면_예외() {
        given(spaceRepository.findById(any())).willReturn(Optional.empty());
        assertThatThrownBy(() -> spaceService.getCongestionPrediction(UUID.randomUUID(), LocalDate.now()))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    @Test
    void getCongestionPrediction_정상반환() {
        Space space = buildSpace(9, 18);
        given(spaceRepository.findById(any())).willReturn(Optional.of(space));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(100L);
        given(reservationRepository.countOccupiedAtSlot(any(), any(), any())).willReturn(30L);

        CongestionPredictionResponse result =
                spaceService.getCongestionPrediction(UUID.randomUUID(), LocalDate.now());

        assertThat(result.hourly()).hasSize(9); // 09~17시 9개
        assertThat(result.hourly().get(0).occupancyRate()).isEqualTo(0.30);
        assertThat(result.hourly().get(0).level()).isEqualTo("LOW");
    }

    @Test
    void getCongestionPrediction_좌석없으면_모두LOW() {
        Space space = buildSpace(9, 11);
        given(spaceRepository.findById(any())).willReturn(Optional.of(space));
        given(seatRepository.countBySpaceIdAndIsEnabledTrue(any())).willReturn(0L);
        given(reservationRepository.countOccupiedAtSlot(any(), any(), any())).willReturn(0L);

        CongestionPredictionResponse result =
                spaceService.getCongestionPrediction(UUID.randomUUID(), LocalDate.now());

        assertThat(result.hourly()).allMatch(h -> "LOW".equals(h.level()));
    }

    private Space buildSpace(int openHour, int closeHour) {
        return Space.create("테스트 공간", 1,
                com.univsitdown.space.domain.SpaceCategory.READING_ROOM,
                LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0),
                4, java.util.List.of(), null);
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.space.service.CongestionPredictionTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/univsitdown/space/service/CongestionPredictionTest.java
git commit -m "test: 혼잡도 예측 서비스 테스트 추가"
```

---

## Task 12: STAT-01 — 이용 통계 쿼리 + 서비스 + 컨트롤러

**Files:**
- Modify: `src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java`
- Create: `src/main/java/com/univsitdown/stat/dto/StatResponse.java`
- Create: `src/main/java/com/univsitdown/stat/service/StatService.java`
- Create: `src/main/java/com/univsitdown/stat/controller/StatController.java`

- [ ] **Step 1: StatResponse DTO**

```java
package com.univsitdown.stat.dto;

import java.util.List;

public record StatResponse(
        String period,
        String from,
        String to,
        long totalMinutes,
        long comparedToPreviousMinutes,
        List<DailyItem> daily,
        List<TopSpaceItem> topSpaces
) {
    public record DailyItem(String date, long minutes) {}
    public record TopSpaceItem(String spaceId, String spaceName, long minutes) {}
}
```

- [ ] **Step 2: ReservationRepository에 통계 native query 추가**

파일 끝에 추가:

```java
// STAT-01: 기간 내 날짜별 이용 시간(분)
@Query(nativeQuery = true, value = """
        SELECT TO_CHAR(start_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD') AS date,
               COALESCE(SUM(EXTRACT(EPOCH FROM (LEAST(end_at, :now) - start_at)) / 60), 0)::BIGINT AS minutes
        FROM reservations
        WHERE user_id = :userId
          AND status IN ('COMPLETED', 'IN_USE', 'SCHEDULED')
          AND start_at >= :from AND start_at < :to
        GROUP BY date
        ORDER BY date
        """)
List<Object[]> findDailyMinutes(@Param("userId") UUID userId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 @Param("now") LocalDateTime now);

// STAT-01: 기간 내 공간별 이용 시간(분) Top 5
@Query(nativeQuery = true, value = """
        SELECT s.space_id::TEXT, sp.name,
               COALESCE(SUM(EXTRACT(EPOCH FROM (LEAST(r.end_at, :now) - r.start_at)) / 60), 0)::BIGINT AS minutes
        FROM reservations r
        JOIN seats s  ON r.seat_id = s.id
        JOIN spaces sp ON s.space_id = sp.id
        WHERE r.user_id = :userId
          AND r.status IN ('COMPLETED', 'IN_USE', 'SCHEDULED')
          AND r.start_at >= :from AND r.start_at < :to
        GROUP BY s.space_id, sp.name
        ORDER BY minutes DESC
        LIMIT 5
        """)
List<Object[]> findTopSpaces(@Param("userId") UUID userId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              @Param("now") LocalDateTime now);
```

- [ ] **Step 3: StatService**

```java
package com.univsitdown.stat.service;

import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.stat.dto.StatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatService {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public StatResponse getStat(UUID userId, String period) {
        LocalDate today = LocalDate.now(KST);
        LocalDate[] range = getRange(period, today);
        LocalDate[] prevRange = getRange(period, today.minus(getPeriodLength(period)));

        LocalDateTime from = range[0].atStartOfDay();
        LocalDateTime to   = range[1].atStartOfDay();
        LocalDateTime prevFrom = prevRange[0].atStartOfDay();
        LocalDateTime prevTo   = prevRange[1].atStartOfDay();
        LocalDateTime now = LocalDateTime.now(KST);

        List<Object[]> dailyRows = reservationRepository.findDailyMinutes(userId, from, to, now);
        List<Object[]> topRows   = reservationRepository.findTopSpaces(userId, from, to, now);
        List<Object[]> prevRows  = reservationRepository.findDailyMinutes(userId, prevFrom, prevTo, now);

        long totalMinutes = dailyRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        long prevMinutes  = prevRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        List<StatResponse.DailyItem> daily = dailyRows.stream()
                .map(r -> new StatResponse.DailyItem((String) r[0], ((Number) r[1]).longValue()))
                .toList();

        List<StatResponse.TopSpaceItem> topSpaces = topRows.stream()
                .map(r -> new StatResponse.TopSpaceItem(
                        (String) r[0], (String) r[1], ((Number) r[2]).longValue()))
                .toList();

        return new StatResponse(period, range[0].format(DATE_FMT), range[1].minusDays(1).format(DATE_FMT),
                totalMinutes, totalMinutes - prevMinutes, daily, topSpaces);
    }

    private LocalDate[] getRange(String period, LocalDate base) {
        return switch (period.toUpperCase()) {
            case "WEEKLY"  -> new LocalDate[]{base.with(java.time.DayOfWeek.MONDAY),
                                              base.with(java.time.DayOfWeek.MONDAY).plusWeeks(1)};
            case "MONTHLY" -> new LocalDate[]{base.withDayOfMonth(1),
                                              base.withDayOfMonth(1).plusMonths(1)};
            case "YEARLY"  -> new LocalDate[]{base.withDayOfYear(1),
                                              base.withDayOfYear(1).plusYears(1)};
            default        -> throw new com.univsitdown.global.exception.BusinessException(
                                      com.univsitdown.global.exception.ErrorCode.STAT_INVALID_PERIOD);
        };
    }

    private java.time.temporal.TemporalAmount getPeriodLength(String period) {
        return switch (period.toUpperCase()) {
            case "WEEKLY"  -> java.time.Period.ofWeeks(1);
            case "MONTHLY" -> java.time.Period.ofMonths(1);
            case "YEARLY"  -> java.time.Period.ofYears(1);
            default        -> java.time.Period.ofWeeks(1);
        };
    }
}
```

- [ ] **Step 4: StatController**

```java
package com.univsitdown.stat.controller;

import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.stat.dto.StatResponse;
import com.univsitdown.stat.service.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    @GetMapping("/me")
    public StatResponse getMyStat(
            @RequestParam(defaultValue = "WEEKLY") String period,
            @AuthenticationPrincipal UserPrincipal principal) {
        return statService.getStat(principal.getId(), period);
    }
}
```

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/univsitdown/stat/ \
        src/main/java/com/univsitdown/reservation/repository/ReservationRepository.java
git commit -m "feat: STAT-01 내 이용 통계 API 구현 (기간별 집계 + Top 5 공간)"
```

---

## Task 13: 통계 테스트

**Files:**
- Create: `src/test/java/com/univsitdown/stat/service/StatServiceTest.java`
- Create: `src/test/java/com/univsitdown/stat/controller/StatControllerTest.java`

- [ ] **Step 1: StatServiceTest**

```java
package com.univsitdown.stat.service;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.reservation.repository.ReservationRepository;
import com.univsitdown.stat.dto.StatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StatServiceTest {

    @Mock ReservationRepository reservationRepository;
    @InjectMocks StatService statService;

    @Test
    void getStat_WEEKLY_정상반환() {
        given(reservationRepository.findDailyMinutes(any(), any(), any(), any()))
                .willReturn(List.of(new Object[]{"2026-05-05", 90L}));
        given(reservationRepository.findTopSpaces(any(), any(), any(), any()))
                .willReturn(List.of(new Object[]{"space-id", "제1열람실", 90L}));

        StatResponse result = statService.getStat(UUID.randomUUID(), "WEEKLY");

        assertThat(result.period()).isEqualTo("WEEKLY");
        assertThat(result.totalMinutes()).isEqualTo(90L);
        assertThat(result.topSpaces()).hasSize(1);
    }

    @Test
    void getStat_MONTHLY_정상반환() {
        given(reservationRepository.findDailyMinutes(any(), any(), any(), any()))
                .willReturn(List.of());
        given(reservationRepository.findTopSpaces(any(), any(), any(), any()))
                .willReturn(List.of());

        StatResponse result = statService.getStat(UUID.randomUUID(), "MONTHLY");
        assertThat(result.period()).isEqualTo("MONTHLY");
        assertThat(result.totalMinutes()).isZero();
    }

    @Test
    void getStat_잘못된period_예외() {
        assertThatThrownBy(() -> statService.getStat(UUID.randomUUID(), "INVALID"))
                .isInstanceOf(BusinessException.class);
    }
}
```

- [ ] **Step 2: StatControllerTest**

```java
package com.univsitdown.stat.controller;

import com.univsitdown.stat.dto.StatResponse;
import com.univsitdown.stat.service.StatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatController.class)
class StatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean StatService statService;

    @Test
    @WithMockUser
    void getMyStat_200() throws Exception {
        given(statService.getStat(any(), eq("WEEKLY")))
                .willReturn(new StatResponse("WEEKLY", "2026-05-04", "2026-05-10",
                        90L, 10L, List.of(), List.of()));

        mockMvc.perform(get("/api/stats/me").param("period", "WEEKLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("WEEKLY"))
                .andExpect(jsonPath("$.totalMinutes").value(90));
    }
}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.stat.*" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/com/univsitdown/stat/
git commit -m "test: StatService·컨트롤러 테스트 추가"
```

---

## Task 14: USER-03 프로필 사진 업로드 (로컬 저장)

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.yml`
- Modify: `src/main/java/com/univsitdown/user/domain/User.java`
- Modify: `src/main/java/com/univsitdown/user/service/UserService.java`
- Modify: `src/main/java/com/univsitdown/user/controller/UserController.java`
- Create: `src/main/java/com/univsitdown/global/config/WebMvcConfig.java`

- [ ] **Step 1: application.yml에 upload-dir 기본값 추가**

`springdoc:` 블록 위에 추가:

```yaml
app:
  upload-dir: ${APP_UPLOAD_DIR:./uploads}
```

- [ ] **Step 2: application-local.yml에 upload-dir 추가**

```yaml
app:
  upload-dir: ./uploads
```

- [ ] **Step 3: WebMvcConfig — 업로드 디렉토리 정적 서빙**

```java
package com.univsitdown.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
```

- [ ] **Step 4: User.updateProfileImageUrl 메서드 추가**

`User` 클래스의 `update(...)` 메서드 아래에 추가:

```java
public void updateProfileImageUrl(String profileImageUrl) {
    this.profileImageUrl = profileImageUrl;
}
```

- [ ] **Step 5: UserService에 updateProfileImage 추가**

`UserService`에 추가. `@Value`와 `import java.io.*`, `java.nio.file.*`, `java.util.UUID` 필요:

```java
@Value("${app.upload-dir:./uploads}")
private String uploadDir;

@Transactional
public UserResponse updateProfileImage(UUID userId, org.springframework.web.multipart.MultipartFile file) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

    String ext = getExtension(file.getOriginalFilename());
    String filename = userId + "_" + UUID.randomUUID() + "." + ext;
    java.nio.file.Path dir  = java.nio.file.Paths.get(uploadDir, "profiles", userId.toString());
    java.nio.file.Path dest = dir.resolve(filename);

    try {
        java.nio.file.Files.createDirectories(dir);
        file.transferTo(dest.toFile());
    } catch (java.io.IOException e) {
        throw new RuntimeException("파일 저장 실패", e);
    }

    String url = "/uploads/profiles/" + userId + "/" + filename;
    user.updateProfileImageUrl(url);
    return UserResponse.from(user);
}

private String getExtension(String originalFilename) {
    if (originalFilename == null || !originalFilename.contains(".")) return "jpg";
    return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
}
```

- [ ] **Step 6: UserController에 POST /api/users/me/profile-image 추가**

```java
@PostMapping(value = "/me/profile-image", consumes = "multipart/form-data")
public UserResponse uploadProfileImage(
        @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
        @AuthenticationPrincipal UserPrincipal principal) {
    return userService.updateProfileImage(principal.getId(), file);
}
```

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/application.yml \
        src/main/resources/application-local.yml \
        src/main/java/com/univsitdown/global/config/WebMvcConfig.java \
        src/main/java/com/univsitdown/user/domain/User.java \
        src/main/java/com/univsitdown/user/service/UserService.java \
        src/main/java/com/univsitdown/user/controller/UserController.java
git commit -m "feat: USER-03 프로필 사진 업로드 API 구현 (로컬 파일 저장)"
```

---

## Task 15: 프로필 사진 업로드 테스트

**Files:**
- Create: `src/test/java/com/univsitdown/user/controller/ProfileImageControllerTest.java`

- [ ] **Step 1: ProfileImageControllerTest 작성**

```java
package com.univsitdown.user.controller;

import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class ProfileImageControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserService userService;

    @Test
    @WithMockUser
    void uploadProfileImage_200() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "profile.jpg", "image/jpeg", "test-image".getBytes());

        given(userService.updateProfileImage(any(), any()))
                .willReturn(new UserResponse(UUID.randomUUID().toString(), "test@test.com",
                        "테스터", null, null, "/uploads/profiles/test/file.jpg", "USER",
                        Instant.now().toString()));

        mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(mockFile)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value("/uploads/profiles/test/file.jpg"));
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew test --tests "com.univsitdown.user.controller.ProfileImageControllerTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 전체 테스트 실행 확인**

```bash
./gradlew test 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL, 모든 테스트 통과

- [ ] **Step 4: 최종 커밋**

```bash
git add src/test/java/com/univsitdown/user/controller/ProfileImageControllerTest.java
git commit -m "test: 프로필 사진 업로드 컨트롤러 테스트 추가"
```

---

## 완료 체크리스트

- [ ] NOTI-01 `GET /api/notices` — 공지사항 목록 조회
- [ ] NOTI-02 `GET /api/notices/{id}` — 공지사항 상세 조회
- [ ] SPACE-03 `GET /api/spaces/{id}/congestion` — 혼잡도 예측 (과거 4주 평균)
- [ ] SPACE-04 `POST /api/spaces/{id}/favorite` — 즐겨찾기 추가
- [ ] SPACE-05 `DELETE /api/spaces/{id}/favorite` — 즐겨찾기 해제
- [ ] STAT-01 `GET /api/stats/me` — 내 이용 통계
- [ ] USER-03 `POST /api/users/me/profile-image` — 프로필 사진 업로드
- [ ] 전체 테스트 통과
