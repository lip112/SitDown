package com.univsitdown.global.security;

import com.univsitdown.user.domain.UserRole;
import java.util.UUID;

public record UserPrincipal(UUID userId, UserRole role) {}
