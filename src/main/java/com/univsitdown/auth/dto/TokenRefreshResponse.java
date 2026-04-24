package com.univsitdown.auth.dto;

public record TokenRefreshResponse(String accessToken, long accessTokenExpiresIn) {}
