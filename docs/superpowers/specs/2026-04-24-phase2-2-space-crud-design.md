# Phase 2-2: Space CRUD 설계 명세

> 작성일: 2026-04-24 | 대상: UNIV SITDOWN 백엔드

## 1. 범위

이 스펙은 공간(Space) 도메인의 CRUD API를 구현한다.

**포함**
- `SPACE-01` GET /api/spaces — 공간 목록 조회 (카테고리·키워드 필터, 페이지네이션)
- `SPACE-02` GET /api/spaces/{id} — 공간 상세 조회
- `ADMIN-01` POST /api/admin/spaces — (관리자) 공간 생성

**제외 (후속 Phase)**
- `SPACE-03` 혼잡도 예측 — Phase 5 (Redis)
- `SPACE-04/05` 즐겨찾기 추가/해제 — Phase 3 (JWT 인증 후)

---

## 2. 데이터 모델

### Flyway 마이그레이션: `V3__create_space_table.sql`

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

### Space Entity (`space/domain/Space.java`)

- `id`: UUID, `@GeneratedValue(strategy = GenerationType.UUID)`
- `name`: String, not null
- `floor`: int, not null
- `category`: SpaceCategory enum, `@Enumerated(EnumType.STRING)`
- `openTime`: LocalTime, not null
- `closeTime`: LocalTime, not null
- `maxReservationHours`: int, not null, default 4
- `features`: `List<String>`, `@Column(columnDefinition = "text[]")` + `StringListConverter`
- `thumbnailUrl`: String, nullable

Setter 없음. `Space.create(...)` 정적 팩토리 메서드로만 생성.

### SpaceCategory Enum (`space/domain/SpaceCategory.java`)

```
READING_ROOM, STUDY_ROOM, PC_ROOM, LECTURE_ROOM
```

### StringListConverter (`global/converter/StringListConverter.java`)

`AttributeConverter<List<String>, String[]>` 구현.
- `convertToDatabaseColumn`: `List<String>` → `String[]`
- `convertToEntityAttribute`: `String[]` → `List<String>`

---

## 3. DTO 설계

모두 Java Record 사용.

### `SpaceListItemResponse` (SPACE-01 목록 응답)

| 필드 | 타입 | 비고 |
|---|---|---|
| id | String | UUID.toString() |
| name | String | |
| floor | int | |
| category | String | enum.name() |
| totalSeats | int | Phase 2-3 전까지 0 (stub) |
| availableSeats | int | Phase 2-3 전까지 0 (stub) |
| congestion | String | Phase 5 전까지 "LOW" (stub) |
| openTime | String | "HH:mm:ss" |
| closeTime | String | "HH:mm:ss" |
| features | List\<String\> | |
| thumbnailUrl | String | nullable |

### `SpaceDetailResponse` (SPACE-02 상세 응답, 관리자 생성 응답 공용)

SpaceListItemResponse 필드 모두 포함 + 추가:

| 필드 | 타입 | 비고 |
|---|---|---|
| rows | int | Phase 2-3 전까지 0 (stub) |
| columns | int | Phase 2-3 전까지 0 (stub) |
| maxReservationHours | int | |
| images | List\<String\> | Phase 2-2에서 빈 리스트 |
| isFavorite | boolean | Phase 3 전까지 false (stub) |

### `CreateSpaceRequest` (ADMIN-01 요청)

| 필드 | 타입 | 검증 |
|---|---|---|
| name | String | `@NotBlank` |
| floor | int | `@Min(1)` |
| category | SpaceCategory | `@NotNull` |
| openTime | LocalTime | `@NotNull` |
| closeTime | LocalTime | `@NotNull` |
| maxReservationHours | int | `@Min(1) @Max(8)`, 기본값 4 |
| features | List\<String\> | optional |
| thumbnailUrl | String | optional |

---

## 4. Repository

### `SpaceRepository extends JpaRepository<Space, UUID>`

```java
@Query("""
    SELECT s FROM Space s
    WHERE (:category IS NULL OR s.category = :category)
      AND (:keyword IS NULL OR s.name LIKE %:keyword%)
    """)
Page<Space> findByFilters(SpaceCategory category, String keyword, Pageable pageable);
```

---

## 5. Service

### `SpaceService`

| 메서드 | 트랜잭션 | 반환 타입 | 예외 |
|---|---|---|---|
| `getSpaces(category, keyword, pageable)` | `readOnly` | `PageResponse<SpaceListItemResponse>` | — |
| `getSpace(id)` | `readOnly` | `SpaceDetailResponse` | `SpaceNotFoundException` |
| `createSpace(request)` | 쓰기 | `SpaceDetailResponse` | — |

### `SpaceNotFoundException` (`space/exception/SpaceNotFoundException.java`)

```java
public class SpaceNotFoundException extends BusinessException {
    public SpaceNotFoundException() { super(ErrorCode.SPACE_NOT_FOUND); }
}
```

---

## 6. Controller

### `SpaceController` — `GET /api/spaces`, `GET /api/spaces/{id}`

- `@RequestMapping("/api/spaces")`
- 목록: `@RequestParam(required = false)` category, keyword, page(기본 0), size(기본 20)
- 상세: `@PathVariable UUID id`
- Phase 3 전이라 `X-User-Id` 헤더 불필요 (조회 API)

### `AdminSpaceController` — `POST /api/admin/spaces`

- `@RequestMapping("/api/admin/spaces")`
- `@Valid @RequestBody CreateSpaceRequest`
- 응답: `201 Created` + `SpaceDetailResponse`
- Phase 3 전이라 `X-User-Id` 헤더 불필요

---

## 7. 테스트 계획

### `SpaceServiceTest` (Mockito 단위 테스트)

| 케이스 | 검증 포인트 |
|---|---|
| 목록 조회 — 필터 없음 | PageResponse 구조, stub 값 (totalSeats=0, congestion="LOW") |
| 목록 조회 — category 필터 | repository에 category 파라미터 전달 여부 |
| 상세 조회 정상 | SpaceDetailResponse 필드 매핑, isFavorite=false |
| 상세 조회 — 없는 ID | `SpaceNotFoundException` throw |
| 공간 생성 정상 | save 호출, SpaceDetailResponse 반환 |

### `SpaceControllerTest` (@WebMvcTest)

| 케이스 | 검증 포인트 |
|---|---|
| `GET /api/spaces` 200 | PageResponse JSON 구조 |
| `GET /api/spaces/{id}` 200 | SpaceDetailResponse JSON |
| `GET /api/spaces/{uuid-없음}` 404 | 에러 코드 `SPACE-001` |
| `POST /api/admin/spaces` 201 | Location 헤더 또는 응답 body |
| `POST /api/admin/spaces` 400 — name 누락 | Validation 에러 |

---

## 8. Stub 필드 정리 (후속 Phase 교체 예정)

| 필드 | 현재 값 | 교체 시점 | 교체 내용 |
|---|---|---|---|
| `totalSeats` | 0 | Phase 2-3 | Seat count 쿼리 |
| `availableSeats` | 0 | Phase 2-3 | 예약 미존재 좌석 count |
| `rows`, `columns` | 0 | Phase 2-3 | Seat max row/col 쿼리 |
| `congestion` | "LOW" | Phase 5 | Redis 집계값 |
| `isFavorite` | false | Phase 3 | 사용자 즐겨찾기 여부 |
| `images` | `[]` | 미정 | S3 이미지 URL 목록 |
