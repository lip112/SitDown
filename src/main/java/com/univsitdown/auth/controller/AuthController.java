package com.univsitdown.auth.controller;

import com.univsitdown.auth.dto.*;
import com.univsitdown.auth.service.AuthService;
import com.univsitdown.global.security.CurrentUser;
import com.univsitdown.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/email/send")
    public ResponseEntity<EmailSendResponse> sendEmailCode(@Valid @RequestBody EmailSendRequest request) {
        return ResponseEntity.ok(authService.sendEmailCode(request.email()));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<EmailVerifyResponse> verifyEmailCode(@Valid @RequestBody EmailVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyEmailCode(request.email(), request.code()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CurrentUser UserPrincipal principal) {
        if (principal != null) {
            authService.logout(principal.userId());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request.email());
        return ResponseEntity.noContent().build();
    }
}
