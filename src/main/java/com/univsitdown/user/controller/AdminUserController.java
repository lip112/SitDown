package com.univsitdown.user.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.global.security.CurrentUser;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.user.dto.AdminUpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> getUsers(Pageable pageable) {
        return ResponseEntity.ok(userService.getUsers(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUserByAdmin(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID id) {
        userService.deleteUser(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
