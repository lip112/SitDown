# Phase 6 — 운영 준비 설계 문서

> 작성일: 2026-05-04 | 범위: 로깅 / MDC / 예외처리 보완 / Actuator

## 1. 목표

장애가 발생해도 원인 파악이 가능한 운영 기반을 만든다.
구체적으로: 요청 단위 traceId 추적, 구조화된 파일 로그, 예외처리 완결성, 서비스 상태 엔드포인트 제공.

## 2. 구현 범위

| 항목 | 설명 |
|---|---|
| MDC traceId 필터 | 요청마다 traceId를 MDC에 심어 모든 로그에 자동 포함 |
| logback-spring.xml | 콘솔 + 날짜별 롤링 파일 로그 (무기한 보관) |
| GlobalExceptionHandler 보완 | Security 예외 포함 5가지 핸들러 추가 |
| Spring Boot Actuator | 전체 엔드포인트 노출, `/actuator/**` 인증 없이 접근 |

## 3. MDC traceId 필터

### 클래스: `global/filter/MdcTraceFilter`

- `OncePerRequestFilter` 구현
- 요청 수신 시: `UUID` 16자리로 `traceId` 생성 → `MDC.put("traceId", id)`
- 응답 헤더에 `X-Trace-Id: {traceId}` 추가 (클라이언트 디버깅용)
- `finally` 블록에서 `MDC.clear()` 호출 (스레드 풀 오염 방지)

### SecurityConfig 변경

`MdcTraceFilter`를 `JwtFilter` 앞에 `addFilterBefore`로 등록. traceId가 JwtFilter 로그에도 포함되도록 하기 위함.

### GlobalExceptionHandler 변경

`generateTraceId()` 제거 → `MDC.get("traceId")`로 교체.
MDC 값이 null인 경우(필터 미통과 시) fallback으로 UUID 새로 생성.

## 4. logback-spring.xml

위치: `src/main/resources/logback-spring.xml`

### 콘솔 패턴
```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId:-no-trace}] %-5level %logger{36} - %msg%n
```

### 파일 롤링
- Appender: `RollingFileAppender`
- 활성 파일: `logs/app.log`
- 롤링 파일: `logs/app.%d{yyyy-MM-dd}.log` (날짜별)
- 롤링 정책: `TimeBasedRollingPolicy`
- 보관 기간: 무기한 (maxHistory 없음)
- 단일 파일 최대: 100MB (`SizeAndTimeBasedRollingPolicy` 사용)

### 프로파일 설정 (`<springProfile>`)
- `local`: 콘솔 + 파일 모두 활성
- 기타(dev, prod): 동일 (별도 분기 없음, 파일 경로는 환경변수로 오버라이드 가능)

## 5. GlobalExceptionHandler 보완

### 추가 핸들러 5가지

| 예외 | HTTP | 에러코드 | 발생 상황 |
|---|---|---|---|
| `AccessDeniedException` | 403 | `COMMON-100` | 권한 없는 API 접근 (일반 사용자 → 관리자 API) |
| `AuthenticationException` | 401 | `AUTH-201` | Spring Security 인증 실패 |
| `HttpMessageNotReadableException` | 400 | `COMMON-100` | JSON 파싱 실패 (잘못된 body 형식) |
| `NoHandlerFoundException` | 404 | `COMMON-100` | 없는 경로 접근 |
| `MethodArgumentTypeMismatchException` | 400 | `COMMON-100` | 경로변수 타입 불일치 (UUID 형식 오류 등) |

### SecurityConfig 보완

`exceptionHandling()` 설정에서:
- `authenticationEntryPoint`: 401 JSON 응답 반환 (현재는 Spring Security 기본 HTML 응답)
- `accessDeniedHandler`: 403 JSON 응답 반환

> Spring Security 예외는 필터 체인 안에서 발생하므로 `@RestControllerAdvice`만으로는 잡히지 않음. `exceptionHandling()`에서 직접 응답을 써야 함.

## 6. Spring Boot Actuator

### build.gradle.kts 추가
```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

### application.yml 추가
```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
  info:
    env:
      enabled: true

info:
  app:
    name: univ-sitdown
    version: 0.0.1
```

### SecurityConfig 보완
`/actuator/**` 경로 `permitAll()` 추가.

## 7. 파일 변경 목록

| 파일 | 변경 유형 |
|---|---|
| `global/filter/MdcTraceFilter.java` | 신규 |
| `global/config/SecurityConfig.java` | 수정 (필터 등록, exceptionHandling, actuator permitAll) |
| `global/exception/GlobalExceptionHandler.java` | 수정 (traceId MDC 연동, 핸들러 5개 추가) |
| `src/main/resources/logback-spring.xml` | 신규 |
| `src/main/resources/application.yml` | 수정 (management 설정 추가) |
| `build.gradle.kts` | 수정 (actuator 의존성 추가) |
