# UNIV SITDOWN API 명세서

> 버전: 1.0  |  작성일: 2026.04.22  |  백엔드 REST API Specification

## 1. 문서 개요

### 1.1 목적
본 문서는 UNIV SITDOWN 시스템의 백엔드 서버가 클라이언트(Flutter 앱)에 제공하는 REST API 규약을 정의한다.

### 1.2 기술 스택
- 백엔드: Java 17 + Spring Boot 3.5.x
- DB: PostgreSQL 16 (메인), Redis 7.2 (캐시/분산 락)
- 인프라: AWS (ECS Fargate, RDS, ElastiCache)
- API 스타일: RESTful JSON

### 1.3 기본 URL

```
Production : https://api.univ-sitdown.com/api
Develop    : https://dev-api.univ-sitdown.com/api
Local      : http://localhost:8080/api
```

### 1.4 공통 규약

#### Content-Type
- 모든 요청/응답은 `application/json; charset=UTF-8`
- 파일 업로드는 `multipart/form-data`

#### 인증 방식
인증이 필요한 엔드포인트는 `Authorization` 헤더에 JWT Access Token을 실어 호출한다.

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### 날짜/시간 포맷
- 모든 날짜·시간은 ISO 8601 UTC 형식 (예: `2026-04-22T09:00:00Z`)
- 클라이언트에서 KST(Asia/Seoul, UTC+9)로 변환하여 표시

#### 페이지네이션
- 목록 조회 API는 `page`(0-based), `size`(기본 20, 최대 100) 쿼리 파라미터 지원

---

## 2. 공통 응답 포맷

### 2.1 성공 응답
성공 시 HTTP 2xx 상태와 함께 리소스 자체 또는 아래 포맷의 응답을 반환한다.

#### 단일 리소스
```json
{
  "id": "a3f9...",
  "name": "제1열람실",
  "floor": 3
}
```

#### 목록 (페이지네이션)
```json
{
  "content": [ { "...": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8,
  "hasNext": true
}
```

### 2.2 에러 응답
모든 에러는 아래 포맷을 따른다. 클라이언트는 `code`를 기준으로 분기하고, `message`는 사용자에게 표시하거나 로그로 남긴다.

```json
{
  "code": "SEAT-002",
  "message": "이미 예약된 좌석입니다.",
  "timestamp": "2026-04-22T09:00:00Z",
  "traceId": "abc-123-def-456",
  "path": "/api/reservations"
}
```

### 2.3 HTTP 상태 코드

| 상태 코드 | 의미 / 사용 시점 |
|---|---|
| `200 OK` | 조회·수정·취소 등 성공 |
| `201 Created` | 회원가입, 예약 생성 등 리소스 신규 생성 성공 |
| `204 No Content` | 본문 없는 성공 (로그아웃, 즐겨찾기 해제 등) |
| `400 Bad Request` | 입력값 검증 실패, 비즈니스 규칙 위반 |
| `401 Unauthorized` | 토큰 없음/만료/위조 |
| `403 Forbidden` | 권한 없음 (일반 사용자의 관리자 API 호출 등) |
| `404 Not Found` | 리소스가 존재하지 않음 |
| `409 Conflict` | 중복·경합 발생 (동시 예약 충돌 등) |
| `423 Locked` | 계정 잠김 |
| `429 Too Many Requests` | Rate limit 초과 |
| `500 Internal Server Error` | 서버 내부 오류 |

---

## 3. 공통 Enum 정의

### 3.1 SpaceCategory (공간 카테고리)

| value | 표시명 | 비고 |
|---|---|---|
| `READING_ROOM` | 열람실 | 개인 학습 중심 |
| `STUDY_ROOM` | 스터디룸 | 그룹 학습, 예약제 |
| `PC_ROOM` | PC실 | PC가 제공되는 공간 |
| `LECTURE_ROOM` | 강의실 | 비정규 시간에 개방 |

### 3.2 SeatStatus (좌석 상태)

| value | 표시 | 설명 |
|---|---|---|
| `AVAILABLE` | 사용 가능 (초록) | 선택하여 예약 가능 |
| `OCCUPIED` | 사용 중 (진회색) | 다른 사용자가 이용 중 |
| `UNAVAILABLE` | 선택 불가 (연회색) | 고장/점검 등으로 비활성화 |
| `RESERVED` | 예약됨 (연파랑) | 해당 시간대에 타인 예약 존재 |

### 3.3 ReservationStatus (예약 상태)

| value | 설명 |
|---|---|
| `SCHEDULED` | 예약 확정, 시작 시간 전 |
| `IN_USE` | 현재 이용 중 |
| `COMPLETED` | 정상 종료 |
| `CANCELED` | 사용자/관리자에 의해 취소됨 |
| `NO_SHOW` | 시작 후 일정 시간 내 체크인 미수행으로 자동 취소 |

### 3.4 CongestionLevel (혼잡도)

| value | 표시명 | 기준 (점유율) |
|---|---|---|
| `LOW` | 여유 | < 40% |
| `NORMAL` | 보통 | 40% ~ 75% |
| `HIGH` | 혼잡 | > 75% |

---

## 4. 전체 API 목록

| API ID | Method | Endpoint | 설명 |
|---|---|---|---|
| AUTH-01 | POST | `/api/auth/signup` | 회원가입 |
| AUTH-02 | POST | `/api/auth/email/send` | 이메일 인증 코드 발송 |
| AUTH-03 | POST | `/api/auth/email/verify` | 이메일 인증 코드 확인 |
| AUTH-04 | POST | `/api/auth/login` | 로그인 (JWT 발급) |
| AUTH-05 | POST | `/api/auth/refresh` | 토큰 갱신 |
| AUTH-06 | POST | `/api/auth/logout` | 로그아웃 |
| AUTH-07 | POST | `/api/auth/password/reset` | 비밀번호 재설정 메일 발송 |
| USER-01 | GET | `/api/users/me` | 내 정보 조회 |
| USER-02 | PATCH | `/api/users/me` | 내 정보 수정 |
| USER-03 | POST | `/api/users/me/profile-image` | 프로필 사진 업로드 |
| SPACE-01 | GET | `/api/spaces` | 공간 목록 조회 |
| SPACE-02 | GET | `/api/spaces/{id}` | 공간 상세 조회 |
| SPACE-03 | GET | `/api/spaces/{id}/congestion` | 혼잡도 예측 조회 |
| SPACE-04 | POST | `/api/spaces/{id}/favorite` | 즐겨찾기 추가 |
| SPACE-05 | DELETE | `/api/spaces/{id}/favorite` | 즐겨찾기 해제 |
| SEAT-01 | GET | `/api/spaces/{id}/seats` | 좌석 배치 및 상태 조회 |
| SEAT-02 | GET | `/api/seats/{id}` | 좌석 상세 조회 |
| RSV-01 | POST | `/api/reservations` | 예약 생성 ★ |
| RSV-02 | GET | `/api/reservations/me` | 내 예약 목록 조회 |
| RSV-03 | GET | `/api/reservations/{id}` | 예약 상세 조회 |
| RSV-04 | PATCH | `/api/reservations/{id}/extend` | 예약 연장 |
| RSV-05 | DELETE | `/api/reservations/{id}` | 예약 취소 |
| STAT-01 | GET | `/api/stats/me` | 내 이용 통계 조회 |
| NOTI-01 | GET | `/api/notices` | 공지사항 목록 조회 |
| NOTI-02 | GET | `/api/notices/{id}` | 공지사항 상세 조회 |
| ADMIN-01 | POST | `/api/admin/spaces` | (관리자) 공간 생성 |
| ADMIN-02 | POST | `/api/admin/spaces/{id}/seats/grid` | (관리자) 좌석 행/열 일괄 생성 |
| ADMIN-03 | PATCH | `/api/admin/seats/{id}` | (관리자) 좌석 상태 변경 |

---

## 5. API 상세 명세

### 5.1 인증 / 회원 (AUTH)

---

#### [AUTH-01] 회원가입

```
POST /api/auth/signup
```

| 항목 | 내용 |
|---|---|
| 설명 | 이메일 인증을 완료한 사용자가 비밀번호와 개인 정보를 입력해 회원가입을 완료한다. |
| 인증 | 불필요 (단, 이메일 인증 선행 필요) |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | string | O | 이메일 주소 (이메일 인증 완료된 값) |
| `password` | string | O | 비밀번호 (8자 이상, 영문/숫자/특수문자 포함) |
| `name` | string | O | 이름 (2자 이상 20자 이하) |
| `phone` | string | X | 전화번호 (010-1234-5678 형식) |
| `affiliation` | string | X | 소속 (예: 학생, 대학원생) |

**요청 예시**
```json
{
  "email": "student@univ.com",
  "password": "P@ssw0rd!",
  "name": "김학생",
  "phone": "010-1234-5678",
  "affiliation": "학생"
}
```

**Response (201 Created)**
```json
{
  "userId": "a3f9b2c1-...",
  "email": "student@univ.com",
  "name": "김학생",
  "createdAt": "2026-04-22T09:00:00Z"
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `AUTH-101` | 이메일 형식 오류 | 유효한 이메일을 입력해 주세요. |
| 400 | `AUTH-102` | 비밀번호 정책 위반 | 비밀번호는 8자 이상, 영문/숫자/특수문자를 포함해야 합니다. |
| 400 | `AUTH-103` | 이메일 미인증 | 이메일 인증을 먼저 완료해 주세요. |
| 409 | `AUTH-104` | 이메일 중복 | 이미 가입된 이메일입니다. |

---

#### [AUTH-02] 이메일 인증 코드 발송

```
POST /api/auth/email/send
```

| 항목 | 내용 |
|---|---|
| 설명 | 회원가입 시 사용할 이메일로 6자리 인증 코드를 발송한다. 코드는 3분간 유효. |
| 인증 | 불필요 |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | string | O | 인증 코드를 받을 이메일 |

**Response (200 OK)**
```json
{
  "email": "student@univ.com",
  "expiresAt": "2026-04-22T09:03:00Z"
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `AUTH-101` | 이메일 형식 오류 | 유효한 이메일을 입력해 주세요. |
| 409 | `AUTH-104` | 이메일 중복 | 이미 가입된 이메일입니다. |
| 429 | `AUTH-105` | 발송 제한 | 잠시 후 다시 시도해 주세요. (1분 1회) |

> 📌 **구현 참고**: Redis에 `key=email:verify:{email}, value={code}, TTL=180s`로 저장. 재발송은 rate limit(1분 1회)을 둔다.

---

#### [AUTH-03] 이메일 인증 코드 확인

```
POST /api/auth/email/verify
```

| 항목 | 내용 |
|---|---|
| 설명 | 발송된 6자리 인증 코드를 확인. 성공 시 해당 이메일은 3분간 "인증 완료" 상태. |
| 인증 | 불필요 |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | string | O | 인증 대상 이메일 |
| `code` | string | O | 6자리 숫자 코드 |

**Response (200 OK)**
```json
{ "verified": true }
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `AUTH-111` | 코드 불일치 | 인증 코드가 올바르지 않습니다. |
| 400 | `AUTH-112` | 코드 만료 | 인증 코드가 만료되었습니다. 재발송해 주세요. |

---

#### [AUTH-04] 로그인

```
POST /api/auth/login
```

| 항목 | 내용 |
|---|---|
| 설명 | 이메일과 비밀번호를 검증하고 JWT Access Token과 Refresh Token을 발급한다. |
| 인증 | 불필요 |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | string | O | 가입된 이메일 |
| `password` | string | O | 비밀번호 |

**Response (200 OK)**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "dGhpcy1pcy1yZWZyZXNoLXRva2Vu...",
  "accessTokenExpiresIn": 1800,
  "user": {
    "id": "a3f9b2c1-...",
    "email": "student@univ.com",
    "name": "김학생",
    "role": "USER"
  }
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 401 | `AUTH-201` | 자격 증명 실패 | 이메일 또는 비밀번호가 올바르지 않습니다. |
| 423 | `AUTH-202` | 계정 잠김 | 로그인 5회 실패로 계정이 잠겼습니다. |

> 📌 **구현 참고**: `accessToken` TTL은 30분, `refreshToken` TTL은 14일 권장. Refresh Token은 Redis에 저장하여 로그아웃/재발급을 관리한다.

---

#### [AUTH-05] 토큰 갱신

```
POST /api/auth/refresh
```

| 항목 | 내용 |
|---|---|
| 설명 | 만료된 Access Token을 Refresh Token으로 갱신한다. |
| 인증 | Refresh Token |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `refreshToken` | string | O | 발급받은 Refresh Token |

**Response (200 OK)**
```json
{
  "accessToken": "eyJhbGciOi...",
  "accessTokenExpiresIn": 1800
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 401 | `AUTH-211` | Refresh Token 만료 | 다시 로그인해 주세요. |
| 401 | `AUTH-212` | Refresh Token 위조 | 다시 로그인해 주세요. |

---

#### [AUTH-06] 로그아웃

```
POST /api/auth/logout
```

| 항목 | 내용 |
|---|---|
| 설명 | 현재 사용자의 Refresh Token을 무효화한다. Access Token은 만료 시까지 유효하므로 클라이언트에서도 삭제해야 한다. |
| 인증 | Access Token |

**Response**: `204 No Content`

---

### 5.2 사용자 (USER)

---

#### [USER-01] 내 정보 조회

```
GET /api/users/me
```

| 항목 | 내용 |
|---|---|
| 설명 | 현재 로그인한 사용자의 프로필 정보를 반환한다. |
| 인증 | Access Token |

**Response (200 OK)**
```json
{
  "id": "a3f9b2c1-...",
  "email": "student@univ.com",
  "name": "김학생",
  "phone": "010-1234-5678",
  "affiliation": "학생",
  "profileImageUrl": "https://cdn.univ-sitdown.com/profile/a3f9.jpg",
  "role": "USER",
  "createdAt": "2025-03-01T00:00:00Z"
}
```

---

#### [USER-02] 내 정보 수정

```
PATCH /api/users/me
```

| 항목 | 내용 |
|---|---|
| 설명 | 이름, 전화번호, 소속 등 수정 가능한 필드만 갱신. 이메일은 변경 불가. |
| 인증 | Access Token |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | string | X | 변경할 이름 |
| `phone` | string | X | 변경할 전화번호 |
| `affiliation` | string | X | 변경할 소속 |

**Response (200 OK)**: 갱신된 사용자 정보 (USER-01과 동일 포맷)

> 📌 **구현 참고**: 전달되지 않은 필드는 변경하지 않는다(null과 미전달을 구분할 것).

---

### 5.3 공간 (SPACE)

---

#### [SPACE-01] 공간 목록 조회

```
GET /api/spaces
```

| 항목 | 내용 |
|---|---|
| 설명 | 공간 목록 조회. 카테고리와 키워드 필터링 가능. 각 공간의 현재 혼잡도 포함. |
| 인증 | Access Token |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `category` | enum | X | `SpaceCategory` (미지정 시 전체) |
| `keyword` | string | X | 공간명 검색어 |
| `page` | int | X | 페이지 번호 (기본 0) |
| `size` | int | X | 페이지 크기 (기본 20, 최대 100) |

**Response (200 OK)**
```json
{
  "content": [
    {
      "id": "space-001",
      "name": "제1열람실",
      "floor": 3,
      "category": "READING_ROOM",
      "totalSeats": 804,
      "availableSeats": 523,
      "congestion": "NORMAL",
      "openTime": "06:00:00",
      "closeTime": "22:00:00",
      "features": ["콘센트", "조용함"],
      "thumbnailUrl": "https://cdn.../spaces/001.jpg"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 6,
  "totalPages": 1,
  "hasNext": false
}
```

> 📌 **구현 참고**: 혼잡도와 `availableSeats`는 Redis에 캐시(TTL 30초)하여 제공. 캐시 미스 시 DB `COUNT` 쿼리 실행.

---

#### [SPACE-02] 공간 상세 조회

```
GET /api/spaces/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 특정 공간의 상세 정보 반환. |
| 인증 | Access Token |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 공간 ID |

**Response (200 OK)**
```json
{
  "id": "space-001",
  "name": "제1열람실",
  "floor": 3,
  "category": "READING_ROOM",
  "totalSeats": 804,
  "availableSeats": 523,
  "rows": 8,
  "columns": 10,
  "congestion": "NORMAL",
  "openTime": "06:00:00",
  "closeTime": "22:00:00",
  "maxReservationHours": 4,
  "features": ["콘센트", "조용함", "와이파이"],
  "images": ["https://cdn.../spaces/001-1.jpg"],
  "isFavorite": true
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 404 | `SPACE-001` | 공간 없음 | 공간을 찾을 수 없습니다. |

---

#### [SPACE-03] 혼잡도 예측 조회

```
GET /api/spaces/{id}/congestion
```

| 항목 | 내용 |
|---|---|
| 설명 | 공간의 시간대별 혼잡도 예측(막대 그래프용) 데이터 반환. |
| 인증 | Access Token |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 공간 ID |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `date` | string(YYYY-MM-DD) | X | 조회 날짜 (기본 오늘) |

**Response (200 OK)**
```json
{
  "spaceId": "space-001",
  "date": "2026-04-22",
  "hourly": [
    { "hour": 8,  "occupancyRate": 0.32, "level": "LOW" },
    { "hour": 12, "occupancyRate": 0.68, "level": "NORMAL" },
    { "hour": 18, "occupancyRate": 0.85, "level": "HIGH" }
  ]
}
```

---

### 5.4 좌석 (SEAT)

---

#### [SEAT-01] 좌석 배치 및 상태 조회

```
GET /api/spaces/{id}/seats
```

| 항목 | 내용 |
|---|---|
| 설명 | 특정 공간의 모든 좌석 배치와 현재 상태 반환. 클라이언트는 이 응답으로 좌석 그리드를 렌더링. |
| 인증 | Access Token |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 공간 ID |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `at` | string(ISO8601) | X | 기준 시각 (미전달 시 현재) |

**Response (200 OK)**
```json
{
  "spaceId": "space-001",
  "rows": 8,
  "columns": 10,
  "seats": [
    {
      "id": "seat-a-12",
      "label": "A-12",
      "row": 1,
      "column": 2,
      "status": "AVAILABLE",
      "features": ["콘센트", "창가"]
    }
  ]
}
```

> 📌 **구현 참고**: 좌석 상태는 실시간성이 중요하므로 Redis 캐시 TTL을 짧게(10초 이내) 설정하거나 캐시 생략. 예약 생성/취소 시 해당 공간 캐시를 즉시 무효화.

---

### 5.5 예약 (RSV) — 핵심 API

---

#### [RSV-01] 예약 생성 ★

```
POST /api/reservations
```

| 항목 | 내용 |
|---|---|
| 설명 | 지정 좌석에 대해 시작/종료 시간을 지정하여 예약을 생성. 동시 예약 충돌을 방지하기 위해 서버에서 비관적 락 또는 Redis 분산 락으로 보호. |
| 인증 | Access Token |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `seatId` | string(UUID) | O | 예약할 좌석 ID |
| `startAt` | string(ISO8601) | O | 시작 일시 (UTC) |
| `endAt` | string(ISO8601) | O | 종료 일시 (UTC) |

**요청 예시**
```json
{
  "seatId": "seat-a-12",
  "startAt": "2026-04-22T09:00:00Z",
  "endAt": "2026-04-22T13:00:00Z"
}
```

**Response (201 Created)**
```json
{
  "id": "rsv-abc-123",
  "seatId": "seat-a-12",
  "seatLabel": "A-12",
  "spaceId": "space-001",
  "spaceName": "제1열람실",
  "startAt": "2026-04-22T09:00:00Z",
  "endAt": "2026-04-22T13:00:00Z",
  "durationHours": 4,
  "status": "SCHEDULED",
  "createdAt": "2026-04-22T08:55:00Z"
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `RSV-001` | 운영 시간 외 | 운영 시간 외에는 예약할 수 없습니다. |
| 400 | `RSV-002` | 최대 이용 시간 초과 | 최대 이용 시간은 4시간입니다. |
| 400 | `RSV-003` | 종료 < 시작 | 시작 시간이 종료 시간보다 빨라야 합니다. |
| 404 | `SEAT-001` | 좌석 없음 | 좌석을 찾을 수 없습니다. |
| 409 | `RSV-004` | 좌석 중복 예약 | 이미 해당 시간대에 예약된 좌석입니다. |
| 409 | `RSV-005` | 사용자 동시 예약 제한 | 진행 중 또는 예정 예약이 이미 존재합니다. |

> 📌 **구현 참고**: 이 API는 이 프로젝트의 핵심. 구현 순서:
> 1. `SELECT ... FOR UPDATE`로 seat row lock
> 2. 시간대 겹침 검증 (`existsOverlapping`)
> 3. 예약 insert
> 4. Redis 공간 캐시 무효화
>
> DB에 `(seat_id, tsrange)` 기반 EXCLUDE 제약을 추가해 이중 방어선을 둔다.

---

#### [RSV-02] 내 예약 목록 조회

```
GET /api/reservations/me
```

| 항목 | 내용 |
|---|---|
| 설명 | 현재 사용자의 예약 목록을 상태별로 조회. 진행 중 / 지난 / 취소 내역 탭에 대응. |
| 인증 | Access Token |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | enum | X | `ACTIVE`(SCHEDULED+IN_USE) / `PAST`(COMPLETED) / `CANCELED` |
| `page` | int | X | 기본 0 |
| `size` | int | X | 기본 20 |

**Response (200 OK)**
```json
{
  "content": [
    {
      "id": "rsv-abc-123",
      "seatLabel": "A-12",
      "spaceName": "제1열람실",
      "spaceFloor": 3,
      "startAt": "2026-04-22T09:00:00Z",
      "endAt": "2026-04-22T13:00:00Z",
      "status": "IN_USE",
      "remainingSeconds": 8130
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "hasNext": false
}
```

> 📌 **구현 참고**: `remainingSeconds`는 `IN_USE` 상태일 때만 의미가 있으며, 서버 시간 기준으로 계산. 클라이언트는 이 값을 초기값으로 받아 로컬 타이머를 돌린다.

---

#### [RSV-04] 예약 연장

```
PATCH /api/reservations/{id}/extend
```

| 항목 | 내용 |
|---|---|
| 설명 | 진행 중인 예약의 종료 시간을 연장. 다음 예약과 충돌하거나 최대 연장 시간 초과 시 실패. |
| 인증 | Access Token |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 예약 ID |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `additionalMinutes` | int | O | 추가할 시간(분). 기본 정책: 최대 120분 |

**Response (200 OK)**
```json
{
  "id": "rsv-abc-123",
  "endAt": "2026-04-22T14:00:00Z",
  "extendedCount": 1
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `RSV-011` | 연장 불가 상태 | 진행 중인 예약만 연장할 수 있습니다. |
| 409 | `RSV-012` | 후속 예약과 충돌 | 다음 예약과 겹쳐 연장할 수 없습니다. |
| 400 | `RSV-013` | 최대 연장 초과 | 더 이상 연장할 수 없습니다. |

---

#### [RSV-05] 예약 취소

```
DELETE /api/reservations/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 자신의 예약을 취소. 시작 전/이용 중 모두 취소 가능. |
| 인증 | Access Token |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 예약 ID |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `reason` | string | X | 취소 사유 (감사 로그용) |

**Response**: `204 No Content`

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 403 | `RSV-021` | 본인 예약 아님 | 본인의 예약만 취소할 수 있습니다. |
| 400 | `RSV-022` | 이미 종료됨 | 이미 종료된 예약은 취소할 수 없습니다. |

---

### 5.6 통계 및 공지 (STAT / NOTI)

---

#### [STAT-01] 내 이용 통계 조회

```
GET /api/stats/me
```

| 항목 | 내용 |
|---|---|
| 설명 | 사용자의 기간별 이용 시간 통계와 주요 이용 공간 Top N 반환. |
| 인증 | Access Token |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `period` | enum | O | `WEEKLY` / `MONTHLY` / `YEARLY` |
| `from` | string(YYYY-MM-DD) | X | 조회 시작일 (미전달 시 기본 기간) |
| `to` | string(YYYY-MM-DD) | X | 조회 종료일 |

**Response (200 OK)**
```json
{
  "period": "WEEKLY",
  "from": "2026-04-13",
  "to": "2026-04-19",
  "totalMinutes": 750,
  "comparedToPreviousMinutes": 150,
  "daily": [
    { "date": "2026-04-13", "minutes": 90 },
    { "date": "2026-04-14", "minutes": 180 }
  ],
  "topSpaces": [
    { "spaceId": "space-001", "spaceName": "제1열람실", "minutes": 510 }
  ]
}
```

---

#### [NOTI-01] 공지사항 목록 조회

```
GET /api/notices
```

| 항목 | 내용 |
|---|---|
| 설명 | 공지사항 목록을 카테고리별로 조회. |
| 인증 | Access Token |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `category` | enum | X | `ALL` / `INFO` / `MAINTENANCE` / `EVENT` (기본 `ALL`) |
| `page` | int | X | 기본 0 |
| `size` | int | X | 기본 20 |

**Response (200 OK)**
```json
{
  "content": [
    {
      "id": "noti-001",
      "title": "도서관 이용 안내",
      "category": "INFO",
      "publishedAt": "2026-05-18T00:00:00Z",
      "isNew": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 12,
  "totalPages": 1,
  "hasNext": false
}
```

---

### 5.7 관리자 (ADMIN)

이하 API는 `role=ADMIN` 권한을 가진 사용자만 호출 가능. 일반 사용자 호출 시 `403 Forbidden` 반환.

---

#### [ADMIN-02] 좌석 행/열 일괄 생성

```
POST /api/admin/spaces/{id}/seats/grid
```

| 항목 | 내용 |
|---|---|
| 설명 | 공간에 대해 행 × 열 크기의 좌석을 한 번에 생성. 기존 좌석 유무에 따라 동작 변경. |
| 인증 | Access Token (ADMIN) |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 공간 ID |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `rows` | int | O | 행 수 (1 ~ 20) |
| `columns` | int | O | 열 수 (1 ~ 20) |
| `labelPrefix` | string | X | 좌석 라벨 접두사 (예: A, B) |
| `overwrite` | boolean | X | 기존 좌석 삭제 후 재생성 여부 (기본 false) |

**요청 예시**
```json
{
  "rows": 8,
  "columns": 10,
  "labelPrefix": "A",
  "overwrite": false
}
```

**Response (200 OK)**
```json
{
  "spaceId": "space-001",
  "createdCount": 80,
  "rows": 8,
  "columns": 10
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `ADMIN-001` | 크기 초과 | 행과 열은 각각 최대 20까지 허용됩니다. |
| 409 | `ADMIN-002` | 기존 좌석 충돌 | 이미 좌석이 존재합니다. `overwrite=true`로 재생성하세요. |

> 📌 **구현 참고**: label 생성 규칙은 `{labelPrefix}-{순번}` 형식으로 일관되게. Batch insert로 한 번에 처리 (`JdbcTemplate` 배치 또는 `JPA saveAll` 주의).

---

## 6. 동시성 및 실시간 처리 정책

### 6.1 예약 동시성 처리

좌석 예약은 동일 시간대에 복수 사용자가 동시에 요청할 수 있는 대표적인 경합 시나리오다. 서버는 아래 중 최소 하나의 방식으로 원자성을 보장해야 한다.

#### 권장 구현 (이중 방어선)

1. **애플리케이션 레벨**: PostgreSQL 비관적 락 (`SELECT ... FOR UPDATE`) + `@Transactional`
2. **DB 레벨**: 예약 테이블에 `(seat_id, 시간 범위)` 기반 EXCLUDE 제약 추가 (PostgreSQL `btree_gist`)
3. **(선택)** 공간 단위 분산 락: Redisson의 `RLock`으로 `seat_id` 단위 락

#### PostgreSQL EXCLUDE 제약 예시

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservation ADD CONSTRAINT no_overlap
EXCLUDE USING gist (
  seat_id WITH =,
  tsrange(start_at, end_at, '[)') WITH &&
)
WHERE (status IN ('SCHEDULED','IN_USE','EXTENDED'));
```

### 6.2 좌석 상태 캐싱

- 공간 목록의 혼잡도/여유 좌석 수: TTL 30초 Redis 캐시
- 좌석 배치 상태 (SEAT-01): TTL 10초 또는 캐시 생략 (실시간성 우선)
- 예약 생성/취소/연장 시 해당 공간의 모든 관련 캐시를 `@CacheEvict`로 즉시 무효화

### 6.3 클라이언트 동기화

- 좌석 선택 화면은 진입 시점 외에 포커스 복귀 시에도 재조회
- 예약 생성 API가 409를 반환하면 즉시 좌석 배치 화면을 재조회하여 최신 상태 표시
- (향후) WebSocket 또는 SSE로 좌석 상태 변경을 푸시할 수 있음

---

## 7. 보안 및 권한

### 7.1 JWT 정책

- Access Token: HS256 또는 RS256 서명, TTL 30분
- Refresh Token: 랜덤 문자열, Redis에 `{userId: refreshToken}` 저장, TTL 14일
- 로그아웃 시 Redis의 Refresh Token 제거
- Access Token에는 최소한의 claim만 포함 (`sub`, `role`, `exp`)

### 7.2 비밀번호 정책

- 저장: BCrypt (cost factor 10 이상)
- 복잡도: 8자 이상, 영문/숫자/특수문자 중 2종 이상
- 로그인 5회 실패 시 5분간 계정 잠금

### 7.3 API 권한 매트릭스

| API 그룹 | Guest | USER | ADMIN | 비고 |
|---|:---:|:---:|:---:|---|
| AUTH (로그인/가입) | ✅ | - | - | 비로그인 접근 |
| USER (내 정보) | ❌ | ✅ | ✅ | 본인 정보만 |
| SPACE/SEAT 조회 | ❌ | ✅ | ✅ | 로그인 필요 |
| RSV (예약) | ❌ | ✅ | ✅ | 본인 예약만 수정/취소 |
| STAT/NOTI | ❌ | ✅ | ✅ | - |
| ADMIN | ❌ | ❌ | ✅ | 관리자 전용 |

### 7.4 Rate Limiting

- 이메일 인증 코드 발송: 동일 이메일 1분 1회
- 로그인: IP당 5분에 20회 (무차별 대입 방지)
- 일반 API: 사용자당 100 req/min (Redis 기반 Token Bucket)

---

## 8. 전체 에러 코드 목록

클라이언트는 아래 에러 코드를 기준으로 사용자 메시지를 매핑한다. 코드 체계는 `{도메인}-{3자리 숫자}`를 따른다.

| 코드 | HTTP | 상황 | 메시지 (사용자 표시용) |
|---|---|---|---|
| `AUTH-101` | 400 | 이메일 형식 오류 | 유효한 이메일을 입력해 주세요. |
| `AUTH-102` | 400 | 비밀번호 정책 위반 | 비밀번호는 8자 이상, 영문/숫자/특수문자를 포함해야 합니다. |
| `AUTH-103` | 400 | 이메일 미인증 | 이메일 인증을 먼저 완료해 주세요. |
| `AUTH-104` | 409 | 이메일 중복 | 이미 가입된 이메일입니다. |
| `AUTH-105` | 429 | 발송 제한 | 잠시 후 다시 시도해 주세요. |
| `AUTH-111` | 400 | 인증 코드 불일치 | 인증 코드가 올바르지 않습니다. |
| `AUTH-112` | 400 | 인증 코드 만료 | 인증 코드가 만료되었습니다. 재발송해 주세요. |
| `AUTH-201` | 401 | 자격 증명 실패 | 이메일 또는 비밀번호가 올바르지 않습니다. |
| `AUTH-202` | 423 | 계정 잠김 | 로그인 5회 실패로 계정이 잠겼습니다. |
| `AUTH-211` | 401 | Refresh Token 만료 | 다시 로그인해 주세요. |
| `AUTH-212` | 401 | Refresh Token 위조 | 다시 로그인해 주세요. |
| `USER-001` | 404 | 사용자 없음 | 사용자를 찾을 수 없습니다. |
| `SPACE-001` | 404 | 공간 없음 | 공간을 찾을 수 없습니다. |
| `SEAT-001` | 404 | 좌석 없음 | 좌석을 찾을 수 없습니다. |
| `SEAT-002` | 409 | 좌석 비활성화 | 현재 이용할 수 없는 좌석입니다. |
| `RSV-001` | 400 | 운영 시간 외 | 운영 시간 외에는 예약할 수 없습니다. |
| `RSV-002` | 400 | 최대 이용 시간 초과 | 최대 이용 시간은 4시간입니다. |
| `RSV-003` | 400 | 시간 유효성 오류 | 시작 시간이 종료 시간보다 빨라야 합니다. |
| `RSV-004` | 409 | 좌석 중복 예약 | 이미 해당 시간대에 예약된 좌석입니다. |
| `RSV-005` | 409 | 사용자 동시 예약 제한 | 진행 중 또는 예정 예약이 이미 존재합니다. |
| `RSV-011` | 400 | 연장 불가 상태 | 진행 중인 예약만 연장할 수 있습니다. |
| `RSV-012` | 409 | 연장 충돌 | 다음 예약과 겹쳐 연장할 수 없습니다. |
| `RSV-013` | 400 | 최대 연장 초과 | 더 이상 연장할 수 없습니다. |
| `RSV-021` | 403 | 본인 예약 아님 | 본인의 예약만 취소할 수 있습니다. |
| `RSV-022` | 400 | 이미 종료됨 | 이미 종료된 예약입니다. |
| `ADMIN-001` | 400 | 좌석 크기 초과 | 행과 열은 각각 최대 20까지 허용됩니다. |
| `ADMIN-002` | 409 | 좌석 충돌 | 이미 좌석이 존재합니다. |
| `COMMON-100` | 400 | 입력값 검증 실패 (`@Valid`) | 입력값이 올바르지 않습니다. |
| `COMMON-001` | 500 | 서버 오류 | 잠시 후 다시 시도해 주세요. |
| `COMMON-002` | 503 | 일시 점검 | 서비스 점검 중입니다. |

---

## 9. 개정 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|---|---|---|---|
| 1.0 | 2026.04.22 | - | 최초 작성 (UNIV SITDOWN API 초안) |
