# Phase 4 설계 — 예약 핵심 로직

> 작성일: 2026-04-30 | 상태: 승인됨

## 범위

SEAT 도메인 구축 + RSV 도메인 구축 + 동시성 제어

### 구현 대상 API
- SEAT-01 `GET /api/spaces/{id}/seats` — 좌석 배치 및 상태 조회
- SEAT-02 `GET /api/seats/{id}` — 좌석 상세 조회
- ADMIN-02 `POST /api/admin/spaces/{id}/seats/grid` — 좌석 일괄 생성
- ADMIN-03 `PATCH /api/admin/seats/{id}` — 좌석 상태 변경
- RSV-01 `POST /api/reservations` — 예약 생성 ★
- RSV-02 `GET /api/reservations/me` — 내 예약 목록
- RSV-03 `GET /api/reservations/{id}` — 예약 상세
- RSV-04 `PATCH /api/reservations/{id}/extend` — 예약 연장
- RSV-05 `DELETE /api/reservations/{id}` — 예약 취소

---

## 결정 사항

| 항목 | 결정 | 이유 |
|------|------|------|
| 좌석 상태 조회 | DB 직접 조회 (캐시 없음) | Phase 5에서 Redis 캐시 추가 예정 |
| 동시성 방어선 | 비관적 락 + DB EXCLUDE 제약 (2겹) | Redisson은 Phase 5 추가 예정 |
| 예약 상태 전환 | 조회 시점 동적 계산 | 스케줄러는 Phase 6 고도화 예정 |

---

## 도메인 구조

### Seat Entity

패키지: `com.univsitdown.space.domain`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | UUID | PK |
| space | Space | FK (ManyToOne, LAZY) |
| row | int | 행 번호 (1부터) |
| column | int | 열 번호 (1부터) |
| label | String | 표시 라벨 (예: A-12) |
| isEnabled | boolean | 관리자 설정 활성/비활성 |
| features | List\<String\> | 좌석 태그 (StringListConverter) |

### SeatStatus Enum

패키지: `com.univsitdown.space.domain`

- `AVAILABLE` — 예약 가능
- `OCCUPIED` — 현재 이용 중 (IN_USE 예약 존재)
- `UNAVAILABLE` — isEnabled=false
- `RESERVED` — 해당 시간대 예약 존재 (SCHEDULED)

### Reservation Entity

패키지: `com.univsitdown.reservation.domain`

| 필드 | 타입 | 설명 |
|------|------|------|
| id | UUID | PK |
| user | User | FK (ManyToOne, LAZY) |
| seat | Seat | FK (ManyToOne, LAZY) |
| startAt | LocalDateTime | 시작 일시 (UTC) |
| endAt | LocalDateTime | 종료 일시 (UTC) |
| status | ReservationStatus | SCHEDULED / CANCELED 만 실제 저장 |
| canceledAt | LocalDateTime | 취소 일시 |
| extendedCount | int | 연장 횟수 |

### ReservationStatus Enum

`SCHEDULED`, `IN_USE`(동적), `COMPLETED`(동적), `CANCELED`, `NO_SHOW`

> IN_USE / COMPLETED는 DB에 저장하지 않고 조회 시 동적 계산:
> - `endAt < now` → COMPLETED
> - `startAt <= now <= endAt` → IN_USE
> - `startAt > now` → SCHEDULED

---

## DB 마이그레이션

### V4 — seat 테이블

```sql
CREATE TABLE seat (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  space_id UUID NOT NULL REFERENCES space(id),
  row_num INT NOT NULL,
  col_num INT NOT NULL,
  label VARCHAR(20) NOT NULL,
  is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  features TEXT,
  UNIQUE (space_id, row_num, col_num)
);
```

### V5 — reservation 테이블 + EXCLUDE 제약

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservation (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  seat_id UUID NOT NULL REFERENCES seat(id),
  start_at TIMESTAMP NOT NULL,
  end_at TIMESTAMP NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
  canceled_at TIMESTAMP,
  extended_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  CONSTRAINT no_overlap EXCLUDE USING gist (
    seat_id WITH =,
    tsrange(start_at, end_at, '[)') WITH &&
  ) WHERE (status NOT IN ('CANCELED', 'NO_SHOW'))
);
```

---

## 동시성 전략 (RSV-01)

```
1. seatRepository.findByIdForUpdate(seatId)    ← SELECT FOR UPDATE (row lock)
2. seat.isEnabled() 검증
3. reservationRepository.existsOverlapping()   ← 시간 겹침 검증
4. 사용자 활성 예약 수 검증 (BR-01)
5. reservationRepository.save()               ← INSERT
6. (DB 레벨) EXCLUDE 제약이 race condition 최종 방어
```

---

## 상태 동적 계산

`ReservationStatus.compute(startAt, endAt, storedStatus)` 정적 메서드:
```
if storedStatus == CANCELED or NO_SHOW → 그대로 반환
if endAt < now → COMPLETED
if startAt <= now → IN_USE
else → SCHEDULED
```

---

## 구현 순서

1. Seat 도메인 (V4 마이그레이션 → Entity → Repository)
2. ADMIN-02 좌석 일괄 생성
3. ADMIN-03 좌석 상태 변경
4. SEAT-01 좌석 배치 조회
5. SEAT-02 좌석 상세 조회
6. Reservation 도메인 (V5 마이그레이션 → Entity → Repository)
7. RSV-01 예약 생성 + 동시성 테스트
8. RSV-02 내 예약 목록
9. RSV-03 예약 상세
10. RSV-04 예약 연장
11. RSV-05 예약 취소
