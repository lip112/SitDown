package com.univsitdown.user.controller;

import com.univsitdown.global.response.PageResponse;
import com.univsitdown.global.security.CurrentUser;
import com.univsitdown.global.security.UserPrincipal;
import com.univsitdown.space.dto.SpaceListItemResponse;
import com.univsitdown.space.service.FavoriteService;
import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FavoriteService favoriteService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(userService.getUser(principal.userId()));
    }

    @GetMapping("/me/favorites")
    public PageResponse<SpaceListItemResponse> getMyFavorites(
            @CurrentUser UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return favoriteService.getMyFavorites(principal.userId(), pageable);
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(principal.userId(), request));
    }

    @PostMapping(value = "/me/profile-image", consumes = "multipart/form-data")
    public ResponseEntity<UserResponse> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            @CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(userService.updateProfileImage(principal.userId(), file));
    }
}
