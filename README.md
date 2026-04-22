# UNIV SITDOWN Backend

대학교 좌석 예약 시스템 백엔드. Java 17 + Spring Boot 3.2 기반.

## 🚀 Claude Code와 함께 시작하기

이 프로젝트는 Claude Code와 함께 개발하도록 설계되었습니다.

### 1. 이 폴더 전체를 프로젝트 루트에 복사

```
your-project/
├── CLAUDE.md          ← Claude가 자동으로 읽는 규칙
├── docs/
│   ├── 01-spec.md
│   ├── 02-api.md
│   └── 03-backend-roadmap.md
└── README.md
```

### 2. Claude Code 실행

```bash
cd your-project
claude
```

첫 대화에서 Claude는 자동으로 `CLAUDE.md`를 읽고 프로젝트 규칙을 이해합니다.

### 3. 첫 명령 예시

**Phase 1 계획 세우기:**
```
@docs/03-backend-roadmap.md 의 Phase 1을 읽고
작업 가능한 단위로 쪼개서 계획을 알려줘.
구현은 하지 말고 계획만 보여줘.
```

**API 구현:**
```
@docs/02-api.md 의 AUTH-04 (로그인) API를 구현해줘.
CLAUDE.md 의 규칙을 따라 DTO, Service, Controller, 테스트까지.
```

---

## 📁 문서 구조

| 파일 | 용도 |
|---|---|
| `CLAUDE.md` | **최우선** - Claude Code의 작업 규칙 |
| `docs/01-spec.md` | 기능 명세 (화면, 비즈니스 규칙, 데이터 모델) |
| `docs/02-api.md` | **핵심** - 전체 API 명세, 에러 코드 |
| `docs/03-backend-roadmap.md` | Phase별 학습 로드맵 |

---

## 🎯 개발 플로우

### Phase 1~7 순서대로

1. **Phase 1** 기반 — 환경 세팅, 첫 API
2. **Phase 2** 도메인 — User/Space/Seat CRUD
3. **Phase 3** 인증 — JWT, Spring Security
4. **Phase 4** 예약 ★ — 동시성, 락 (이 프로젝트의 꽃)
5. **Phase 5** Redis — 캐싱, 분산 락
6. **Phase 6** 운영 — 예외 처리, 로깅, 문서화
7. **Phase 7** 배포 — AWS, CI/CD

자세한 내용은 `docs/03-backend-roadmap.md` 참조.

---

## 💡 Claude Code 활용 팁

### 좋은 지시
```
✅ @docs/02-api.md 의 RSV-01 API를 구현해줘. 동시성 테스트도 함께.
✅ Phase 2 완료됐어. 커밋 메시지 제안해줘.
✅ 이 예외 처리가 CLAUDE.md 규칙에 맞는지 리뷰해줘.
```

### 피해야 할 지시
```
❌ 예약 API 만들어줘 (어떤 API인지 불분명)
❌ 적당히 좋게 만들어줘 (규칙 무시하게 됨)
❌ 명세랑 좀 달라도 괜찮아 (나중에 더 큰 문제)
```

### 세션 관리
- Phase 하나 끝나면 `/clear`로 컨텍스트 초기화
- 새 Phase 시작 시 CLAUDE.md 부터 다시 읽히기
- 커밋은 작은 단위로 자주

---

## 🛠 로컬 개발 환경 (Phase 1에서 설정)

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

---

## 📝 커밋 메시지 규칙 (제안)

```
feat(rsv): 예약 생성 API 구현 (RSV-01)
fix(auth): 토큰 만료 시 401 대신 500 반환 문제 수정
test(rsv): 동시 예약 경합 통합 테스트 추가
docs(api): AUTH-04 에러 코드 2개 추가
refactor(space): 공간 목록 조회 N+1 fetch join으로 해결
chore: Flyway V3 마이그레이션 추가
```

---

## 🔗 참고

- 명세 변경이 필요하면 먼저 `docs/02-api.md` 수정 후 개정 이력에 기록
- 새 에러 코드는 `ErrorCode` enum과 `docs/02-api.md` 8장 **둘 다** 업데이트
- 블로그 글감이 나오면 `docs/blog-ideas.md` 같은 파일에 메모해두기
