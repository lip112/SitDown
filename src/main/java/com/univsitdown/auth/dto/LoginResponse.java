package com.univsitdown.auth.dto;

import com.univsitdown.user.domain.User;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresIn,
        LoginUserInfo user
) {
    public record LoginUserInfo(UUID id, String email, String name, String role) {
        public static LoginUserInfo from(User user) {
            return new LoginUserInfo(user.getId(), user.getEmail(), user.getName(), user.getRole().name());
        }
    }
}
