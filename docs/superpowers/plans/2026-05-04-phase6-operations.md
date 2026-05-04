# Phase 6 — 운영 준비 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** MDC 기반 traceId 추적, 파일 롤링 로그, 예외처리 완결성, Actuator 상태 엔드포인트를 추가하여 운영 기반을 갖춘다.

**Architecture:** `MdcTraceFilter`가 모든 요청 진입 시 traceId를 MDC에 심고 응답 헤더로 내보낸다. `logback-spring.xml`은 콘솔/파일 두 Appender에 traceId 패턴을 포함하여 로그를 출력한다. `GlobalExceptionHandler`는 MDC에서 traceId를 읽고 누락된 예외 핸들러 3가지를 추가한다. `SecurityConfig`는 필터 순서(MdcTraceFilter → JwtFilter)를 정돈하고 exceptionHandling 응답을 올바른 JSON으로 개선한다.

**Tech Stack:** Spring Boot 3.5 / Spring Security 6 / SLF4J MDC / Logback SizeAndTimeBasedRollingPolicy / Spring Boot Actuator / Jackson ObjectMapper

---

## 파일 변경 목록

| 파일 | 유형 |
|---|---|
| `src/main/java/com/univsitdown/global/filter/MdcTraceFilter.java` | 신규 |
| `src/test/java/com/univsitdown/global/filter/MdcTraceFilterTest.java` | 신규 |
| `src/main/resources/logback-spring.xml` | 신규 |
| `src/main/java/com/univsitdown/global/exception/GlobalExceptionHandler.java` | 수정 |
| `src/main/java/com/univsitdown/global/config/SecurityConfig.java` | 수정 |
| `src/main/resources/application.yml` | 수정 |
| `build.gradle.kts` | 수정 |

---

## Task 1: MdcTraceFilter 구현

**Files:**
- Create: `src/main/java/com/univsitdown/global/filter/MdcTraceFilter.java`
- Create: `src/test/java/com/univsitdown/global/filter/MdcTraceFilterTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/univsitdown/global/filter/MdcTraceFilterTest.java` 를 아래 내용으로 생성:

```java
package com.univsitdown.global.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTraceFilterTest {

    private final MdcTraceFilter filter = new MdcTraceFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void 요청_처리_중_MDC에_traceId가_저장되고_응답헤더에도_포함된다() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] capturedTraceId = {null};
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws IOException, ServletException {
                capturedTraceId[0] = MDC.get("traceId");
                super.doFilter(req, res);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(capturedTraceId[0]).isNotNull().hasSize(16);
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(capturedTraceId[0]);
    }

    @Test
    void 요청_완료_후_MDC가_비워진다() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void 필터_체인_예외_발생해도_MDC가_비워진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws IOException, ServletException {
                throw new ServletException("test error");
            }
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {}

        assertThat(MDC.get("traceId")).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실행하여 컴파일 에러 확인**

```bash
./gradlew test --tests "com.univsitdown.global.filter.MdcTraceFilterTest" 2>&1 | tail -20
```

예상: `MdcTraceFilter` 클래스가 없으므로 컴파일 에러 발생

- [ ] **Step 3: MdcTraceFilter 구현**

`src/main/java/com/univsitdown/global/filter/MdcTraceFilter.java` 를 아래 내용으로 생성:

```java
package com.univsitdown.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class MdcTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.univsitdown.global.filter.MdcTraceFilterTest" 2>&1 | tail -20
```

예상: `BUILD SUCCESSFUL`, 테스트 3개 모두 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/univsitdown/global/filter/MdcTraceFilter.java \
        src/test/java/com/univsitdown/global/filter/MdcTraceFilterTest.java
git commit -m "feat: MDC traceId 필터 추가"
```

---

## Task 2: logback-spring.xml 추가

**Files:**
- Create: `src/main/resources/logback-spring.xml`

- [ ] **Step 1: logback-spring.xml 생성**

`src/main/resources/logback-spring.xml` 을 아래 내용으로 생성:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <property name="LOG_PATH" value="${LOG_PATH:-logs}"/>
    <property name="LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId:-no-trace}] %-5level %logger{36} - %msg%n"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/app.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>

</configuration>
```

> 로그 레벨(`DEBUG`/`WARN` 등)은 기존 `application-{profile}.yml`의 `logging.level` 설정이 담당한다.
> logback-spring.xml은 포맷·Appender만 관리한다.

- [ ] **Step 2: 애플리케이션 빌드 후 로그 포맷 확인**

```bash
./gradlew build -x test 2>&1 | tail -10
```

예상: `BUILD SUCCESSFUL`. 이후 앱 실행 시 `[no-trace]` 패턴이 포함된 로그가 콘솔에 출력됨.

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/logback-spring.xml
git commit -m "feat: logback-spring.xml 파일 롤링 로그 추가"
```

---

## Task 3: GlobalExceptionHandler 개선

**Files:**
- Modify: `src/main/java/com/univsitdown/global/exception/GlobalExceptionHandler.java`

변경 내용:
1. `generateTraceId()` → `resolveTraceId()` (MDC 우선 조회)
2. 핸들러 3개 추가: `HttpMessageNotReadableException`, `NoResourceFoundException`, `MethodArgumentTypeMismatchException`

- [ ] **Step 1: GlobalExceptionHandler 전체 교체**

`src/main/java/com/univsitdown/global/exception/GlobalExceptionHandler.java` 를 아래로 교체:

```java
package com.univsitdown.global.exception;

import com.univsitdown.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException e, HttpServletRequest request) {
        log.warn("[BusinessException] code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ErrorResponse.of(e.getErrorCode(), resolveTraceId(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.VALIDATION_FAILED.getCode(),
                message,
                Instant.now().toString(),
                resolveTraceId(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("[HttpMessageNotReadableException] path={}", request.getRequestURI());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.VALIDATION_FAILED.getCode(),
                "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해 주세요.",
                Instant.now().toString(),
                resolveTraceId(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {
        log.warn("[NoResourceFoundException] path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                ErrorCode.VALIDATION_FAILED.getCode(),
                "요청한 경로를 찾을 수 없습니다.",
                Instant.now().toString(),
                resolveTraceId(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("[MethodArgumentTypeMismatchException] param={}, value={}", e.getName(), e.getValue());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.VALIDATION_FAILED.getCode(),
                "경로 변수 '" + e.getName() + "'의 형식이 올바르지 않습니다.",
                Instant.now().toString(),
                resolveTraceId(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("[UnexpectedException] path={}", request.getRequestURI(), e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, resolveTraceId(), request.getRequestURI()));
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew build -x test 2>&1 | tail -10
```

예상: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/univsitdown/global/exception/GlobalExceptionHandler.java
git commit -m "feat: GlobalExceptionHandler traceId MDC 연동 및 예외 핸들러 추가"
```

---

## Task 4: SecurityConfig 개선 + Actuator 추가

**Files:**
- Modify: `src/main/java/com/univsitdown/global/config/SecurityConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `build.gradle.kts`

변경 내용:
1. `MdcTraceFilter`를 `JwtFilter` 앞에 등록
2. `exceptionHandling` 핸들러에서 MDC traceId + 올바른 JSON 포맷으로 개선
3. `/actuator/**` permitAll 추가
4. Actuator 의존성 + application.yml 설정 추가

- [ ] **Step 1: build.gradle.kts에 Actuator 의존성 추가**

`build.gradle.kts` 의 `// Web` 아래에 한 줄 추가:

```kotlin
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
```

- [ ] **Step 2: application.yml에 management 설정 추가**

`src/main/resources/application.yml` 파일 끝에 아래를 추가:

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

- [ ] **Step 3: SecurityConfig 전체 교체**

`src/main/java/com/univsitdown/global/config/SecurityConfig.java` 를 아래로 교체:

```java
package com.univsitdown.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.global.filter.MdcTraceFilter;
import com.univsitdown.global.response.ErrorResponse;
import com.univsitdown.global.security.JwtFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final MdcTraceFilter mdcTraceFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/health",
                                "/actuator/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs/**"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(mdcTraceFilter, JwtFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "AUTH-201", "인증이 필요합니다.", request.getRequestURI()))
                        .accessDeniedHandler((request, response, ex) ->
                                writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                                        "COMMON-100", "접근 권한이 없습니다.", request.getRequestURI()))
                )
                .build();
    }

    private void writeErrorResponse(HttpServletResponse response, int status,
                                    String code, String message, String path) throws IOException {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        ErrorResponse body = new ErrorResponse(code, message, Instant.now().toString(), traceId, path);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 4: 전체 빌드 + 테스트 통과 확인**

```bash
./gradlew build 2>&1 | tail -20
```

예상: `BUILD SUCCESSFUL`, 기존 테스트 포함 전체 PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/univsitdown/global/config/SecurityConfig.java \
        src/main/resources/application.yml \
        build.gradle.kts
git commit -m "feat: Actuator 추가, SecurityConfig MDC 연동 및 필터 순서 정리"
```
