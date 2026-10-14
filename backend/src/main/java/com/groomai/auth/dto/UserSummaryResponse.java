package com.groomai.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryResponse(
    UUID userId,
    String email,
    String displayName,
    Instant createdAt
) {}
