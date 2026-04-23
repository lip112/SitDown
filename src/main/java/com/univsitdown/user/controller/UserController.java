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
