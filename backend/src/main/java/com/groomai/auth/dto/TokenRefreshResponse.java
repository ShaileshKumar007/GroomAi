package com.groomai.auth.dto;

public record TokenRefreshResponse(
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
