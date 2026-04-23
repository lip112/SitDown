package com.univsitdown.user.dto;

import com.univsitdown.user.domain.User;

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
                user.getId() != null ? user.getId().toString() : null, // null only in unpersisted entities (unit tests)
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getAffiliation(),
                user.getProfileImageUrl(),
                user.getRole().name(),
                user.getCreatedAt().toString()
        );
    }
}
