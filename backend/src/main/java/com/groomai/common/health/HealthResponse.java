package com.groomai.common.health;

import java.time.Instant;

/**
 * Immutable API response payload for the public health check endpoint.
 * Conforms strictly to docs/API_SPEC.md Section 8.1.
 */
public record HealthResponse(
    String status,
    String service,
    Instant timestamp
) {}
