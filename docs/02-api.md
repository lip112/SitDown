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
Production : http://sitdown.bond/api
OpenAPI    : http://sitdown.bond/api-docs
Swagger UI : http://sitdown.bond/swagger-ui/index.html
Local      : http://localhost:8080/api
```

운영 환경은 Nginx reverse proxy를 통해 같은 도메인에서 프론트와 백엔드를 라우팅한다. HTTPS 적용 후에는 위 운영 URL의 scheme을 `https`로 맞춘다.

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
- 응답 날짜·시간은 KST 기준 `yyyy-MM-dd HH:mm:ss` 형식 (예: `2026-04-22 18:00:00`)
- 요청 날짜·시간은 각 API의 필드 설명을 따른다. 예를 들어 예약 `startAt`, `endAt`은 KST 기준 오프셋 없는 local datetime으로 전달한다.
- 운영 종료 시각 `closeTime`이 `00:00:00`이면 운영일의 자정 종료를 의미한다. 예를 들어 `06:00:00-00:00:00` 공간은 같은 날 `23:42:00` 종료 예약과 다음날 `00:00:00` 종료 예약을 운영 시간 내로 본다.

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
  "timestamp": "2026-04-22 18:00:00",
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

### 3.5 Affiliation (소속)

| value | 표시명 |
|---|---|
| `UNDERGRADUATE` | 학부생 |
| `GRADUATE` | 대학원생 |
| `FACULTY` | 교직원 |
| `ASSISTANT` | 조교 |
| `EXTERNAL` | 외부인 |

---

## 4. 전체 API 목록

| API ID | Method | Endpoint | 설명 |
|---|---|---|---|
| AUTH-01 | POST | `/api/auth/signup` | 회원가입 |
| AUTH-02 | POST | `/api/auth/email/check` | 이메일 중복 확인 |
| AUTH-04 | POST | `/api/auth/login` | 로그인 (JWT 발급) |
| AUTH-05 | POST | `/api/auth/refresh` | 토큰 갱신 |
| AUTH-06 | POST | `/api/auth/logout` | 로그아웃 |
| USER-01 | GET | `/api/users/me` | 내 정보 조회 |
| USER-02 | PATCH | `/api/users/me` | 내 정보 수정 |
| USER-03 | POST | `/api/users/me/profile-image` | 프로필 사진 업로드 |
| USER-04 | GET | `/api/users/me/favorites` | 내 즐겨찾기 공간 목록 조회 |
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
| ADMIN-04 | GET | `/api/admin/users` | (관리자) 회원 목록 조회 |
| ADMIN-05 | GET | `/api/admin/users/{id}` | (관리자) 회원 상세 조회 |
| ADMIN-06 | PATCH | `/api/admin/users/{id}` | (관리자) 회원 정보 수정 |
| ADMIN-07 | DELETE | `/api/admin/users/{id}` | (관리자) 회원 삭제 |
| ADMIN-08 | GET | `/api/admin/dashboard` | (관리자) 대시보드 지표 조회 |
| ADMIN-09 | POST | `/api/admin/notices` | (관리자) 공지사항 등록 |
| ADMIN-10 | PATCH | `/api/admin/notices/{id}` | (관리자) 공지사항 수정 |
| ADMIN-11 | DELETE | `/api/admin/notices/{id}` | (관리자) 공지사항 삭제 |

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
| 설명 | 이메일 중복 확인 후 비밀번호와 개인 정보를 저장해 회원가입을 완료한다. |
| 인증 | 불필요 |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | string | O | 이메일 주소 |
| `password` | string | O | 비밀번호 (8자 이상, 영문/숫자/특수문자 포함) |
| `name` | string | O | 이름 (2자 이상 20자 이하) |
| `phone` | string | X | 전화번호 (010-1234-5678 형식) |
| `affiliation` | enum | X | 소속 (`Affiliation` 참고) |

**요청 예시**
```json
{
  "email": "student@univ.com",
  "password": "P@ssw0rd1!",
  "name": "김학생",
  "phone": "010-1234-5678",
  "affiliation": "UNDERGRADUATE"
}
```

**Response (201 Created)**
```json
{
  "userId": "a3f9b2c1-...",
  "email": "student@univ.com",
  "name": "김학생",
  "createdAt": "2026-04-22 18:00:00"
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `AUTH-101` | 이메일 형식 오류 | 유효한 이메일을 입력해 주세요. |
| 400 | `AUTH-102` | 비밀번호 정책 위반 | 비밀번호는 8자 이상, 영문/숫자/특수문자를 포함해야 합니다. |
| 409 | `AUTH-104` | 이메일 중복 | 이미 가입된 이메일입니다. |

---

#### [AUTH-02] 이메일 중복 확인

```
POST /api/auth/email/check
```

| 항목 | 내용 |
|---|---|
| 설명 | 입력한 이메일이 가입 가능한지 확인한다. 이미 가입된 이메일이면 `AUTH-104`를 반환한다. |
| 인증 | 불필요 |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `email` | string | O | 중복 확인할 이메일 |

**Response (200 OK)**
```json
{
  "email": "student@univ.com",
  "available": true
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `AUTH-101` | 이메일 형식 오류 | 유효한 이메일을 입력해 주세요. |
| 409 | `AUTH-104` | 이메일 중복 | 이미 가입된 이메일입니다. |

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
| 설명 | Access Token이 전달되면 현재 사용자의 Refresh Token을 무효화한다. 토큰 없이 호출해도 성공 응답을 반환하므로 클라이언트는 보유 토큰을 함께 삭제해야 한다. |
| 인증 | 선택 (Access Token) |

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
  "affiliation": "UNDERGRADUATE",
  "profileImageUrl": "/uploads/profiles/a3f9b2c1-.../a3f9b2c1-..._4f2d9c.jpg",
  "role": "USER",
  "createdAt": "2025-03-01 09:00:00"
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
| `affiliation` | enum | X | 변경할 소속 (`Affiliation` 참고) |

**Response (200 OK)**: 갱신된 사용자 정보 (USER-01과 동일 포맷)

> 📌 **구현 참고**: 전달되지 않은 필드는 변경하지 않는다(null과 미전달을 구분할 것).

---

#### [USER-03] 프로필 사진 업로드

```
POST /api/users/me/profile-image
```

| 항목 | 내용 |
|---|---|
| 설명 | 현재 로그인한 사용자의 프로필 사진을 업로드하고, 갱신된 사용자 정보를 반환한다. |
| 인증 | Access Token |
| Content-Type | `multipart/form-data` |

**Request Parts**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `file` | file | O | 업로드할 프로필 이미지 파일 (`jpg`, `png`, `webp`) |

**Response (200 OK)**: 갱신된 사용자 정보 (USER-01과 동일 포맷)

```json
{
  "id": "a3f9b2c1-...",
  "email": "student@univ.com",
  "name": "김학생",
  "phone": "010-1234-5678",
  "affiliation": "UNDERGRADUATE",
  "profileImageUrl": "/uploads/profiles/a3f9b2c1-.../a3f9b2c1-..._4f2d9c.jpg",
  "role": "USER",
  "createdAt": "2025-03-01 09:00:00"
}
```

> 📌 **구현 참고**: 서버는 업로드 파일을 `app.upload-dir` 하위 `profiles/{userId}` 경로에 저장하고, `/uploads/profiles/{userId}/{filename}` 형식의 URL을 사용자 프로필에 저장한다. 확장자나 `Content-Type`만 믿지 않고 파일 시그니처를 검사하여 JPEG, PNG, WebP만 허용한다.

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `USER-002` | 이미지 파일이 아니거나 지원하지 않는 형식 | 이미지 파일만 업로드할 수 있습니다. |
| 401 | `AUTH-201` | 인증 토큰 없음/만료/위조 | 인증이 필요합니다. |

---

#### [USER-04] 내 즐겨찾기 공간 목록 조회

```
GET /api/users/me/favorites
```

| 항목 | 내용 |
|---|---|
| 설명 | 현재 로그인한 사용자가 즐겨찾기한 공간 목록을 반환한다. |
| 인증 | Access Token |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | number | X | 페이지 번호 (기본 0) |
| `size` | number | X | 페이지 크기 (기본 20) |

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
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 401 | `AUTH-201` | 인증 토큰 없음/만료/위조 | 인증이 필요합니다. |

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
| 인증 | 불필요 |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `category` | enum | X | `READING_ROOM` / `STUDY_ROOM` / `PC_ROOM` / `LECTURE_ROOM` (미지정 시 전체) |
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

> 📌 **구현 참고**: `availableSeats`는 현재 예약 상태에 따라 달라지므로 매 요청마다 DB `COUNT` 쿼리로 계산한다.

---

#### [SPACE-02] 공간 상세 조회

```
GET /api/spaces/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 특정 공간의 상세 정보 반환. |
| 인증 | 불필요 (로그인 시 즐겨찾기 여부 반영) |

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
| 인증 | 불필요 |

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

#### [SPACE-04] 즐겨찾기 추가

```
POST /api/spaces/{id}/favorite
```

| 항목 | 내용 |
|---|---|
| 설명 | 현재 사용자의 즐겨찾기 공간에 지정 공간을 추가한다. 이미 추가된 공간이면 성공으로 처리한다. |
| 인증 | Access Token |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 공간 ID |

**Response**: `204 No Content`

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 404 | `SPACE-001` | 공간 없음 | 공간을 찾을 수 없습니다. |

---

#### [SPACE-05] 즐겨찾기 해제

```
DELETE /api/spaces/{id}/favorite
```

| 항목 | 내용 |
|---|---|
| 설명 | 현재 사용자의 즐겨찾기 공간에서 지정 공간을 제거한다. 즐겨찾기 상태가 아니어도 성공으로 처리한다. |
| 인증 | Access Token |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 공간 ID |

**Response**: `204 No Content`

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
| 인증 | 불필요 |

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

#### [SEAT-02] 좌석 상세 조회

```
GET /api/seats/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 특정 좌석의 위치, 상태, 소속 공간 정보를 조회한다. |
| 인증 | 불필요 |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 좌석 ID |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `at` | string(ISO8601) | X | 기준 시각 (미전달 시 현재) |

**Response (200 OK)**
```json
{
  "id": "seat-a-12",
  "label": "A-12",
  "row": 1,
  "column": 2,
  "status": "AVAILABLE",
  "features": ["콘센트", "창가"],
  "spaceId": "space-001",
  "spaceName": "제1열람실"
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 404 | `SEAT-001` | 좌석 없음 | 좌석을 찾을 수 없습니다. |

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
| `startAt` | string(ISO8601 local datetime) | O | 시작 일시 (KST 기준, 오프셋 없이 전달) |
| `endAt` | string(ISO8601 local datetime) | O | 종료 일시 (KST 기준, 오프셋 없이 전달). `closeTime`이 `00:00:00`인 공간은 다음날 `00:00:00` 종료까지 허용 |

**요청 예시**
```json
{
  "seatId": "seat-a-12",
  "startAt": "2026-04-22T09:00:00",
  "endAt": "2026-04-22T13:00:00"
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
  "startAt": "2026-04-22 09:00:00",
  "endAt": "2026-04-22 13:00:00",
  "durationHours": 4,
  "status": "SCHEDULED",
  "createdAt": "2026-04-22 08:55:00"
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
      "startAt": "2026-04-22 09:00:00",
      "endAt": "2026-04-22 13:00:00",
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

#### [RSV-03] 예약 상세 조회

```
GET /api/reservations/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 현재 사용자의 특정 예약 상세 정보를 조회한다. 본인 예약이 아니면 실패한다. |
| 인증 | Access Token |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 예약 ID |

**Response (200 OK)**
```json
{
  "id": "rsv-abc-123",
  "seatId": "seat-a-12",
  "seatLabel": "A-12",
  "spaceId": "space-001",
  "spaceName": "제1열람실",
  "spaceFloor": 3,
  "startAt": "2026-04-22 09:00:00",
  "endAt": "2026-04-22 13:00:00",
  "durationHours": 4,
  "status": "IN_USE",
  "remainingSeconds": 8130,
  "extendedCount": 0,
  "createdAt": "2026-04-22 08:55:00"
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 403 | `RSV-021` | 본인 예약 아님 | 본인의 예약만 취소할 수 있습니다. |
| 404 | `RSV-031` | 예약 없음 | 예약을 찾을 수 없습니다. |

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
  "endAt": "2026-04-22 14:00:00",
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
GET /api/stats/me?from=2026-04-13&to=2026-04-19
```

| 항목 | 내용 |
|---|---|
| 설명 | 사용자의 기간별 이용 시간 통계와 주요 이용 공간 Top N 반환. |
| 인증 | Access Token |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `from` | date | O | 조회 시작일 (`yyyy-MM-dd`) |
| `to` | date | O | 조회 종료일 (`yyyy-MM-dd`) |

**Response (200 OK)**
```json
{
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

`comparedToPreviousMinutes`는 같은 길이의 직전 기간 대비 증감분이다.

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `STAT-001` | `from`/`to` 누락, 날짜 형식 오류, 또는 `from`이 `to`보다 늦은 경우 | 유효하지 않은 조회 기간입니다. |

---

#### [NOTI-01] 공지사항 목록 조회

```
GET /api/notices
```

| 항목 | 내용 |
|---|---|
| 설명 | 공지사항 목록을 카테고리별로 조회. |
| 인증 | 불필요 |

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
      "publishedAt": "2026-05-18 09:00:00",
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

#### [NOTI-02] 공지사항 상세 조회

```
GET /api/notices/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 활성 상태인 공지사항의 상세 내용을 조회한다. |
| 인증 | 불필요 |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 공지사항 ID |

**Response (200 OK)**
```json
{
  "id": "noti-001",
  "title": "도서관 이용 안내",
  "content": "열람실 이용 시 음식을 반입을 금지합니다.",
  "category": "INFO",
  "publishedAt": "2026-05-18 09:00:00"
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 404 | `NOTI-001` | 공지사항 없음 또는 비활성 상태 | 공지사항을 찾을 수 없습니다. |

---

### 5.7 관리자 (ADMIN)

이하 API는 `role=ADMIN` 권한을 가진 사용자만 호출 가능. 일반 사용자 호출 시 `403 Forbidden` 반환.

---

#### [ADMIN-01] 공간 생성

```
POST /api/admin/spaces
```

| 항목 | 내용 |
|---|---|
| 설명 | 공간명, 층, 카테고리, 운영 시간, 최대 예약 시간을 입력해 새 공간을 생성한다. |
| 인증 | Access Token (ADMIN) |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | string | O | 공간명 (최대 100자) |
| `floor` | int | O | 층수 (1 이상) |
| `category` | enum | O | `READING_ROOM` / `STUDY_ROOM` / `PC_ROOM` / `LECTURE_ROOM` |
| `openTime` | string(HH:mm:ss) | O | 운영 시작 시각 |
| `closeTime` | string(HH:mm:ss) | O | 운영 종료 시각. `00:00:00`은 운영일의 자정 종료를 의미 |
| `maxReservationHours` | int | O | 최대 예약 시간 (1 ~ 8) |
| `features` | string[] | X | 공간 편의 기능 목록 |
| `thumbnailUrl` | string | X | 대표 이미지 URL |

**Response (201 Created)**
```json
{
  "id": "space-001",
  "name": "제1열람실",
  "floor": 3,
  "category": "READING_ROOM",
  "totalSeats": 0,
  "availableSeats": 0,
  "rows": 0,
  "columns": 0,
  "congestion": "LOW",
  "openTime": "06:00:00",
  "closeTime": "22:00:00",
  "maxReservationHours": 4,
  "features": ["콘센트", "조용함"],
  "images": [],
  "isFavorite": false
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `COMMON-100` | 입력값 검증 실패 | 입력값이 올바르지 않습니다. |

---

#### [ADMIN-08] 대시보드 지표 조회

```
GET /api/admin/dashboard
```

| 항목 | 내용 |
|---|---|
| 설명 | 관리자 대시보드에 표시할 공간 수와 활성 예약 수를 조회한다. |
| 인증 | Access Token (ADMIN) |

**Response (200 OK)**
```json
{
  "spaceCount": 6,
  "activeReservationCount": 3
}
```

---

#### [ADMIN-09] 공지사항 등록

```
POST /api/admin/notices
```

| 항목 | 내용 |
|---|---|
| 설명 | 관리자 페이지에서 노출할 공지사항을 등록한다. |
| 인증 | Access Token (ADMIN) |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `title` | string | O | 제목 (최대 200자) |
| `content` | string | O | 본문 |
| `category` | enum | O | `INFO` / `MAINTENANCE` / `EVENT` |
| `publishedAt` | datetime | X | 발행 시각. 미입력 시 서버 현재 시각 |
| `expiresAt` | datetime | X | 만료 시각 |

**Response (201 Created)**
```json
{
  "id": "36752627-fdd0-43cf-a74a-e08795f21700",
  "title": "도서관 이용 안내",
  "content": "열람실 이용 시 음식을 반입을 금지합니다.",
  "category": "INFO",
  "publishedAt": "2026-05-14 09:00:00"
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `COMMON-100` | 입력값 검증 실패 | 입력값이 올바르지 않습니다. |

---

#### [ADMIN-10] 공지사항 수정

```
PATCH /api/admin/notices/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 공지사항의 제목, 본문, 카테고리, 발행/만료 시각을 수정한다. 요청에 포함된 필드만 변경한다. |
| 인증 | Access Token (ADMIN) |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `title` | string | X | 제목 (1자 이상 200자 이하) |
| `content` | string | X | 본문 (1자 이상) |
| `category` | enum | X | `INFO` / `MAINTENANCE` / `EVENT` |
| `publishedAt` | datetime | X | 발행 시각 |
| `expiresAt` | datetime | X | 만료 시각 |

**Response (200 OK)**
```json
{
  "id": "36752627-fdd0-43cf-a74a-e08795f21700",
  "title": "수정된 공지",
  "content": "수정된 내용입니다.",
  "category": "EVENT",
  "publishedAt": "2026-05-14 09:00:00"
}
```

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `COMMON-100` | 입력값 검증 실패 | 입력값이 올바르지 않습니다. |
| 404 | `NOTI-001` | 공지사항 없음 | 공지사항을 찾을 수 없습니다. |

---

#### [ADMIN-11] 공지사항 삭제

```
DELETE /api/admin/notices/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 공지사항을 비활성화 처리하여 사용자 목록과 상세 조회에서 제외한다. |
| 인증 | Access Token (ADMIN) |

**Response**

| 상태 | 설명 |
|---|---|
| 204 | 삭제 성공 |

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 404 | `NOTI-001` | 공지사항 없음 | 공지사항을 찾을 수 없습니다. |

---

#### [ADMIN-04] 회원 목록 조회

```
GET /api/admin/users
```

| 항목 | 내용 |
|---|---|
| 설명 | 전체 회원 목록을 페이지 단위로 조회한다. |
| 인증 | Access Token (ADMIN) |

**Query Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | int | X | 기본 0 |
| `size` | int | X | 기본 20 |

**Response (200 OK)**
```json
{
  "content": [
    {
      "id": "user-001",
      "email": "student@univ.com",
      "name": "김학생",
      "phone": "010-1234-5678",
      "affiliation": "UNDERGRADUATE",
      "profileImageUrl": null,
      "role": "USER",
      "createdAt": "2026-04-22 09:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

---

#### [ADMIN-05] 회원 상세 조회

```
GET /api/admin/users/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 회원 ID로 회원 상세 정보를 조회한다. |
| 인증 | Access Token (ADMIN) |

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 404 | `USER-001` | 회원 없음 | 사용자를 찾을 수 없습니다. |

---

#### [ADMIN-06] 회원 정보 수정

```
PATCH /api/admin/users/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 회원의 이름, 전화번호, 소속을 수정한다. |
| 인증 | Access Token (ADMIN) |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | string | X | 이름 (2자 이상 20자 이하) |
| `phone` | string | X | 전화번호 (010-1234-5678 형식) |
| `affiliation` | enum | X | 소속 (`Affiliation` 참고) |

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `COMMON-100` | 입력값 검증 실패 | 입력값이 올바르지 않습니다. |
| 404 | `USER-001` | 회원 없음 | 사용자를 찾을 수 없습니다. |

---

#### [ADMIN-07] 회원 삭제

```
DELETE /api/admin/users/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 회원 ID로 회원을 삭제한다. |
| 인증 | Access Token (ADMIN) |

**Response**

| 상태 | 설명 |
|---|---|
| 204 | 삭제 성공 |

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 404 | `USER-001` | 회원 없음 | 사용자를 찾을 수 없습니다. |

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

#### [ADMIN-03] 좌석 상태 변경

```
PATCH /api/admin/seats/{id}
```

| 항목 | 내용 |
|---|---|
| 설명 | 좌석을 사용 가능 또는 사용 불가 상태로 변경한다. 비활성화된 좌석은 좌석 조회에서 `UNAVAILABLE`로 반환된다. |
| 인증 | Access Token (ADMIN) |

**Path Parameters**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | string(UUID) | O | 좌석 ID |

**Request Body**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `isEnabled` | boolean | O | `true`면 사용 가능, `false`면 사용 불가 |

**Response**: `200 OK`

**Error Responses**

| 상태 | 에러 코드 | 발생 조건 | 메시지 |
|---|---|---|---|
| 400 | `COMMON-100` | 입력값 검증 실패 | 입력값이 올바르지 않습니다. |
| 404 | `SEAT-001` | 좌석 없음 | 좌석을 찾을 수 없습니다. |

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

ALTER TABLE reservations ADD CONSTRAINT no_overlap
EXCLUDE USING gist (
  seat_id WITH =,
  tsrange(start_at, end_at, '[)') WITH &&
)
WHERE (status NOT IN ('CANCELED', 'NO_SHOW'));
```

### 6.2 좌석 상태 캐싱

- 공간 목록의 여유 좌석 수: 현재 시각 기준 실시간 계산
- 좌석 배치 상태 (SEAT-01): `at` 기준 상태가 달라지므로 캐시 생략
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
| SPACE/SEAT 조회 | ✅ | ✅ | ✅ | 조회만 비로그인 접근 |
| RSV (예약) | ❌ | ✅ | ✅ | 본인 예약만 수정/취소 |
| STAT | ❌ | ✅ | ✅ | 개인 통계 |
| NOTI 조회 | ✅ | ✅ | ✅ | 비로그인 접근 |
| ADMIN | ❌ | ❌ | ✅ | 관리자 전용 |

### 7.4 Rate Limiting

- 로그인: IP당 5분에 20회 (무차별 대입 방지)
- 일반 API: 사용자당 100 req/min (Redis 기반 Token Bucket)

---

## 8. 전체 에러 코드 목록

클라이언트는 아래 에러 코드를 기준으로 사용자 메시지를 매핑한다. 코드 체계는 `{도메인}-{3자리 숫자}`를 따른다.

| 코드 | HTTP | 상황 | 메시지 (사용자 표시용) |
|---|---|---|---|
| `AUTH-101` | 400 | 이메일 형식 오류 | 유효한 이메일을 입력해 주세요. |
| `AUTH-102` | 400 | 비밀번호 정책 위반 | 비밀번호는 8자 이상, 영문/숫자/특수문자를 포함해야 합니다. |
| `AUTH-104` | 409 | 이메일 중복 | 이미 가입된 이메일입니다. |
| `AUTH-201` | 401 | 자격 증명 실패 | 이메일 또는 비밀번호가 올바르지 않습니다. |
| `AUTH-202` | 423 | 계정 잠김 | 로그인 5회 실패로 계정이 잠겼습니다. |
| `AUTH-211` | 401 | Refresh Token 만료 | 다시 로그인해 주세요. |
| `AUTH-212` | 401 | Refresh Token 위조 | 다시 로그인해 주세요. |
| `USER-001` | 404 | 사용자 없음 | 사용자를 찾을 수 없습니다. |
| `USER-002` | 400 | 프로필 이미지 형식 오류 | 이미지 파일만 업로드할 수 있습니다. |
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
| `RSV-031` | 404 | 예약 없음 | 예약을 찾을 수 없습니다. |
| `ADMIN-001` | 400 | 좌석 크기 초과 | 행과 열은 각각 최대 20까지 허용됩니다. |
| `ADMIN-002` | 409 | 좌석 충돌 | 이미 좌석이 존재합니다. |
| `NOTI-001` | 404 | 공지사항 없음 | 공지사항을 찾을 수 없습니다. |
| `STAT-001` | 400 | 유효하지 않은 조회 기간 | 유효하지 않은 조회 기간입니다. |
| `COMMON-100` | 400 | 입력값 검증 실패 (`@Valid`) | 입력값이 올바르지 않습니다. |
| `COMMON-001` | 500 | 서버 오류 | 잠시 후 다시 시도해 주세요. |
| `COMMON-002` | 503 | 일시 점검 | 서비스 점검 중입니다. |

---

## 9. 개정 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|---|---|---|---|
| 1.0 | 2026.04.22 | - | 최초 작성 (UNIV SITDOWN API 초안) |
| 1.1 | 2026.05.04 | - | `affiliation` 필드를 자유 문자열 → `Affiliation` Enum으로 변경 (3.5절 추가) |
