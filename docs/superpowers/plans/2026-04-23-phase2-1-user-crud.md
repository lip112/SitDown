# Phase 2-1: User Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** User 엔티티를 DB에 정의하고, 내 정보 조회(USER-01) 및 수정(USER-02) API를 구현한다.

**Architecture:** `user/` 패키지에 domain, repository, service, dto, controller, exception 레이어를 생성한다. Phase 3(JWT)이 완료되기 전까지 컨트롤러는 `X-User-Id` 헤더로 userId를 받는다. Phase 3에서 `@AuthenticationPrincipal`로 교체 예정. `GlobalExceptionHandler`에 `MethodArgumentNotValidException` 핸들러를 추가한다.

**Tech Stack:** Spring Boot 3.5, JPA/Hibernate, Flyway, PostgreSQL, Lombok, Spring Validation, MockMvc, Mockito

---

## 생성/수정 파일 목록

| 작업 | 파일 |
|---|---|
| Create | `src/main/resources/db/migration/V2__create_user_table.sql` |
| Create | `src/main/java/com/univsitdown/user/domain/UserRole.java` |
| Create | `src/main/java/com/univsitdown/user/domain/User.java` |
| Create | `src/main/java/com/univsitdown/user/repository/UserRepository.java` |
| Create | `src/main/java/com/univsitdown/user/exception/UserNotFoundException.java` |
| Create | `src/main/java/com/univsitdown/user/dto/UserResponse.java` |
| Create | `src/main/java/com/univsitdown/user/dto/UpdateUserRequest.java` |
| Create | `src/main/java/com/univsitdown/user/service/UserService.java` |
| Create | `src/main/java/com/univsitdown/user/controller/UserController.java` |
| Create | `src/test/java/com/univsitdown/user/service/UserServiceTest.java` |
| Create | `src/test/java/com/univsitdown/user/controller/UserControllerTest.java` |
| Modify | `src/main/java/com/univsitdown/global/exception/GlobalExceptionHandler.java` |
| Modify | `src/main/java/com/univsitdown/global/config/SecurityConfig.java` |

---

## Task 1: Flyway V2 마이그레이션 (users 테이블)

**Files:**
- Create: `src/main/resources/db/migration/V2__create_user_table.sql`

- [ ] **Step 1: V2 마이그레이션 파일 생성**

```sql
CREATE TABLE users (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    name              VARCHAR(20)  NOT NULL,
    phone             VARCHAR(20),
    affiliation       VARCHAR(100),
    profile_image_url VARCHAR(500),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users (email);
```

- [ ] **Step 2: 앱 기동하여 Flyway 실행 확인**

앱 기동 후 로그에서 확인:
```
Successfully applied 1 migration to schema "public"
```

PostgreSQL에서 직접 확인:
```sql
\d users
```
Expected: id, email, password_hash, name, phone, affiliation, profile_image_url, role, created_at 컬럼 존재

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/db/migration/V2__create_user_table.sql
git commit -m "db: users 테이블 Flyway V2 마이그레이션 추가"
```

---

## Task 2: UserRole enum + User 엔티티

**Files:**
- Create: `src/main/java/com/univsitdown/user/domain/UserRole.java`
- Create: `src/main/java/com/univsitdown/user/domain/User.java`

- [ ] **Step 1: UserRole enum 작성**

```java
package com.univsitdown.user.domain;

public enum UserRole {
    USER, ADMIN
}
```

- [ ] **Step 2: User 엔티티 작성**

```java
package com.univsitdown.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String affiliation;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static User create(String email, String passwordHash, String name,
                              String phone, String affiliation) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.name = name;
        user.phone = phone;
        user.affiliation = affiliation;
        user.role = UserRole.USER;
        user.createdAt = LocalDateTime.now();
        return user;
    }

    public void update(String name, String phone, String affiliation) {
        if (name != null) this.name = name;
        if (phone != null) this.phone = phone;
        if (affiliation != null) this.affiliation = affiliation;
    }
}
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew compileJava 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/univsitdown/user/domain/
git commit -m "feat: User 엔티티 및 UserRole enum 추가"
```

---

## Task 3: UserRepository + UserNotFoundException

**Files:**
- Create: `src/main/java/com/univsitdown/user/repository/UserRepository.java`
- Create: `src/main/java/com/univsitdown/user/exception/UserNotFoundException.java`

- [ ] **Step 1: UserRepository 작성**

```java
package com.univsitdown.user.repository;

import com.univsitdown.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 2: UserNotFoundException 작성**

```java
package com.univsitdown.user.exception;

import com.univsitdown.global.exception.BusinessException;
import com.univsitdown.global.exception.ErrorCode;

public class UserNotFoundException extends BusinessException {
    public UserNotFoundException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
```

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/univsitdown/user/repository/ \
        src/main/java/com/univsitdown/user/exception/
git commit -m "feat: UserRepository, UserNotFoundException 추가"
```

---

## Task 4: DTO 정의

**Files:**
- Create: `src/main/java/com/univsitdown/user/dto/UserResponse.java`
- Create: `src/main/java/com/univsitdown/user/dto/UpdateUserRequest.java`

- [ ] **Step 1: UserResponse Record 작성**

```java
package com.univsitdown.user.dto;

import com.univsitdown.user.domain.User;

import java.time.ZoneOffset;

public record UserResponse(
        String id,
        String email,
        String name,
        String phone,
        String affiliation,
        String profileImageUrl,
        String role,
        String createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getAffiliation(),
                user.getProfileImageUrl(),
                user.getRole().name(),
                user.getCreatedAt().toInstant(ZoneOffset.UTC).toString()
        );
    }
}
```

- [ ] **Step 2: UpdateUserRequest Record 작성**

```java
package com.univsitdown.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 2, max = 20, message = "이름은 2자 이상 20자 이하여야 합니다.")
        String name,

        @Pattern(
                regexp = "^\\d{3}-\\d{4}-\\d{4}$",
                message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)"
        )
        String phone,

        @Size(max = 100, message = "소속은 100자 이하여야 합니다.")
        String affiliation
) {}
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew compileJava 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/univsitdown/user/dto/
git commit -m "feat: UserResponse, UpdateUserRequest DTO 추가"
```

---

## Task 5: GlobalExceptionHandler에 Validation 핸들러 추가

**Files:**
- Modify: `src/main/java/com/univsitdown/global/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: MethodArgumentNotValidException 핸들러 추가**

기존 `handleBusinessException` 메서드 아래에 추가한다:

```java
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.time.Instant;
import java.util.stream.Collectors;

@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException e, HttpServletRequest request) {
    String message = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
    ErrorResponse body = new ErrorResponse(
            "COMMON-100",
            message,
            Instant.now().toString(),
            generateTraceId(),
            request.getRequestURI()
    );
    return ResponseEntity.badRequest().body(body);
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew compileJava 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/univsitdown/global/exception/GlobalExceptionHandler.java
git commit -m "feat: GlobalExceptionHandler에 Validation 에러 핸들러 추가"
```

---

## Task 6: UserService 구현 + 단위 테스트

**Files:**
- Create: `src/main/java/com/univsitdown/user/service/UserService.java`
- Create: `src/test/java/com/univsitdown/user/service/UserServiceTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.univsitdown.user.service;

import com.univsitdown.user.domain.User;
import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.exception.UserNotFoundException;
import com.univsitdown.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getUser_존재하는_사용자_조회_성공() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@univ.com", "hash", "김학생", "010-1234-5678", "학생");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        UserResponse response = userService.getUser(userId);

        assertThat(response.email()).isEqualTo("test@univ.com");
        assertThat(response.name()).isEqualTo("김학생");
    }

    @Test
    void getUser_존재하지않으면_UserNotFoundException() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateUser_이름_변경_성공() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@univ.com", "hash", "김학생", null, "학생");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        UserResponse response = userService.updateUser(userId, new UpdateUserRequest("이름변경", null, null));

        assertThat(response.name()).isEqualTo("이름변경");
    }

    @Test
    void updateUser_존재하지않으면_UserNotFoundException() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(userId, new UpdateUserRequest("이름변경", null, null)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인 (UserService 미구현)**

```bash
./gradlew test --tests "com.univsitdown.user.service.UserServiceTest" 2>&1 | tail -10
```
Expected: FAIL (UserService class not found)

- [ ] **Step 3: UserService 구현**

```java
package com.univsitdown.user.service;

import com.univsitdown.user.domain.User;
import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.exception.UserNotFoundException;
import com.univsitdown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.update(request.name(), request.phone(), request.affiliation());
        return UserResponse.from(user);
    }
}
```

- [ ] **Step 4: 테스트 실행 → 4개 모두 통과 확인**

```bash
./gradlew test --tests "com.univsitdown.user.service.UserServiceTest" 2>&1 | tail -10
```
Expected:
```
UserServiceTest > getUser_존재하는_사용자_조회_성공() PASSED
UserServiceTest > getUser_존재하지않으면_UserNotFoundException() PASSED
UserServiceTest > updateUser_이름_변경_성공() PASSED
UserServiceTest > updateUser_존재하지않으면_UserNotFoundException() PASSED
BUILD SUCCESSFUL
```

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/univsitdown/user/service/ \
        src/test/java/com/univsitdown/user/service/
git commit -m "feat: UserService 구현 및 단위 테스트 추가"
```

---

## Task 7: SecurityConfig 임시 수정 + UserController 구현

**Files:**
- Modify: `src/main/java/com/univsitdown/global/config/SecurityConfig.java`
- Create: `src/main/java/com/univsitdown/user/controller/UserController.java`

- [ ] **Step 1: SecurityConfig — /api/users/** 임시 허용**

`SecurityConfig.java`의 `authorizeHttpRequests` 블록을 아래와 같이 수정한다:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/api/health",
        "/api/users/**",      // TODO: Phase 3에서 제거 — JWT @AuthenticationPrincipal로 교체
        "/swagger-ui/**",
        "/api-docs/**"
    ).permitAll()
    .anyRequest().authenticated()
)
```

- [ ] **Step 2: UserController 작성**

```java
package com.univsitdown.user.controller;

import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// TODO: Phase 3에서 @RequestHeader("X-User-Id")를 @AuthenticationPrincipal로 교체
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }
}
```

- [ ] **Step 3: 빌드 확인**

```bash
./gradlew compileJava 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/univsitdown/user/controller/ \
        src/main/java/com/univsitdown/global/config/SecurityConfig.java
git commit -m "feat: UserController 구현 (Phase 3 전 임시 X-User-Id 헤더 사용)"
```

---

## Task 8: UserController WebMvc 테스트

**Files:**
- Create: `src/test/java/com/univsitdown/user/controller/UserControllerTest.java`

- [ ] **Step 1: 테스트 작성**

```java
package com.univsitdown.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.exception.UserNotFoundException;
import com.univsitdown.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UserResponse SAMPLE_RESPONSE = new UserResponse(
            TEST_USER_ID.toString(), "test@univ.com", "김학생",
            "010-1234-5678", "학생", null, "USER", "2026-04-22T09:00:00Z"
    );

    @Test
    @WithMockUser
    void getMe_정상_조회_200() throws Exception {
        given(userService.getUser(TEST_USER_ID)).willReturn(SAMPLE_RESPONSE);

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", TEST_USER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@univ.com"))
                .andExpect(jsonPath("$.name").value("김학생"));
    }

    @Test
    @WithMockUser
    void getMe_존재하지않는_사용자_404() throws Exception {
        given(userService.getUser(TEST_USER_ID)).willThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", TEST_USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER-001"));
    }

    @Test
    @WithMockUser
    void updateMe_이름_변경_200() throws Exception {
        UserResponse updated = new UserResponse(
                TEST_USER_ID.toString(), "test@univ.com", "이름변경",
                null, "학생", null, "USER", "2026-04-22T09:00:00Z"
        );
        given(userService.updateUser(eq(TEST_USER_ID), any())).willReturn(updated);

        mockMvc.perform(patch("/api/users/me")
                        .header("X-User-Id", TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest("이름변경", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이름변경"));
    }

    @Test
    @WithMockUser
    void updateMe_이름_1자_400() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .header("X-User-Id", TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest("김", null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void updateMe_잘못된_전화번호_형식_400() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .header("X-User-Id", TEST_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateUserRequest(null, "01012345678", null))))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 테스트 실행 → 5개 모두 통과 확인**

```bash
./gradlew test --tests "com.univsitdown.user.controller.UserControllerTest" 2>&1 | tail -15
```
Expected:
```
UserControllerTest > getMe_정상_조회_200() PASSED
UserControllerTest > getMe_존재하지않는_사용자_404() PASSED
UserControllerTest > updateMe_이름_변경_200() PASSED
UserControllerTest > updateMe_이름_1자_400() PASSED
UserControllerTest > updateMe_잘못된_전화번호_형식_400() PASSED
BUILD SUCCESSFUL
```

- [ ] **Step 3: 전체 테스트 통과 확인**

```bash
./gradlew test 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/test/java/com/univsitdown/user/controller/
git commit -m "test: UserController WebMvc 테스트 추가"
```

---

## Phase 2-1 완료 체크리스트

- [ ] Flyway V2 마이그레이션 성공 (users 테이블 생성)
- [ ] `./gradlew test` 전체 통과
- [ ] `GET /api/users/me` Postman으로 직접 호출 확인 (DB에 수동 insert 후)
- [ ] `PATCH /api/users/me` Postman으로 이름 변경 확인
