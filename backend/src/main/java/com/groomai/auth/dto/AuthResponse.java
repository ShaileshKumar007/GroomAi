package com.groomai.auth.dto;

import java.util.UUID;

public record AuthResponse(
    UUID userId,
    String email,
    String displayName,
    String accessToken,
    String refreshToken,
    long expiresIn
) {}
