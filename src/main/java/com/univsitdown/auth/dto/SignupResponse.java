package com.univsitdown.auth.dto;

import com.univsitdown.user.domain.User;
import java.util.UUID;

public record SignupResponse(UUID userId, String email, String name, String createdAt) {
    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getCreatedAt().toString()
        );
    }
}
