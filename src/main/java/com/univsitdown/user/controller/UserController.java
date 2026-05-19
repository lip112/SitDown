package com.univsitdown.user.controller;

import com.univsitdown.global.security.CurrentUser;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.user.dto.ChangePasswordRequest;
import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(userService.getUser(principal.userId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(principal.userId(), request));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/me/profile-image", consumes = "multipart/form-data")
    public ResponseEntity<UserResponse> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            @CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(userService.updateProfileImage(principal.userId(), file));
    }
}
