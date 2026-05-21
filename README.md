# UNIV SITDOWN Backend

대학교 좌석 예약 시스템 백엔드. Java 17 + Spring Boot 3.5 기반.

## 현재 구현 범위

- JWT 기반 인증/인가, 회원가입, 로그인, 토큰 갱신, 로그아웃
- 사용자 프로필 조회/수정, 프로필 이미지 업로드
- 공간/좌석 조회, 관리자 공간 생성, 좌석 그리드 생성, 좌석 상태 변경
- 예약 생성/조회/상세/연장/취소와 좌석 예약 동시성 제어
- 즐겨찾기, 공지사항, 사용자 통계, 관리자 대시보드
- PostgreSQL + Flyway 마이그레이션, Redis 기반 인증 저장소/캐시 fallback
- Springdoc OpenAPI, Docker Compose 운영 배포 메모

## 문서 구조

| 파일 | 용도 |
|---|---|
| `docs/01-spec.md` | 기능 명세 (화면, 비즈니스 규칙, 데이터 모델) |
| `docs/02-api.md` | 전체 API 명세, 에러 코드 |
| `docs/03-backend-roadmap.md` | Phase별 학습 로드맵 |
| `docs/04-deployment.md` | Docker Compose + Nginx 운영 배포 메모 |

## 로컬 개발 환경

```bash
# PostgreSQL + Redis (Docker)
docker compose up -d

# 앱 실행
./gradlew bootRun

# 테스트
./gradlew test

# Swagger UI
open http://localhost:8080/swagger-ui.html
```

## 현재 운영 배포 구조

현재 운영 도메인은 Nginx reverse proxy를 앞에 두고, Docker Compose 내부 서비스로 라우팅한다.

| URL | 대상 |
|---|---|
| `http://sitdown.bond/` | 프론트엔드 |
| `http://sitdown.bond/api/**` | 백엔드 API |
| `http://sitdown.bond/uploads/**` | 업로드 이미지 |
| `http://sitdown.bond/swagger-ui/index.html` | Swagger UI |
| `http://sitdown.bond/api-docs` | OpenAPI JSON |

Nginx는 `/api/**`, `/uploads/**`, `/api-docs`, `/api-docs/`, `/swagger-ui/**`, `/swagger-ui.html`을 백엔드(`backend:8080`)로 보내고, 나머지는 프론트엔드(`frontend:3000`)로 보낸다. 자세한 설정과 장애 대응은 `docs/04-deployment.md`를 참조한다.

운영 CORS는 `application.yml` 기본값보다 `CORS_ALLOWED_ORIGINS` 환경변수가 우선한다. Docker Compose의 `.env`에 운영 도메인을 포함해야 한다.

```env
CORS_ALLOWED_ORIGINS=http://sitdown.bond,http://www.sitdown.bond,https://sitdown.bond,https://www.sitdown.bond
```

## 커밋 메시지 규칙

```
feat(rsv): 예약 생성 API 구현 (RSV-01)
fix(auth): 토큰 만료 시 401 대신 500 반환 문제 수정
test(rsv): 동시 예약 경합 통합 테스트 추가
docs(api): AUTH-04 에러 코드 2개 추가
refactor(space): 공간 목록 조회 N+1 fetch join으로 해결
chore: Flyway V3 마이그레이션 추가
```

## 참고

- 명세 변경이 필요하면 먼저 `docs/02-api.md` 수정 후 개정 이력에 기록
- 새 에러 코드는 `ErrorCode` enum과 `docs/02-api.md` 8장 **둘 다** 업데이트
- DB 스키마 변경은 `src/main/resources/db/migration/`에 Flyway 마이그레이션으로 추가
