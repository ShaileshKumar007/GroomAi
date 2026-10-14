package com.groomai.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response record matching docs/API_SPEC.md Section 1.4.
 *
 * @param timestamp Instant of error occurrence in UTC
 * @param status HTTP status code (e.g., 400, 401, 404, 500)
 * @param error HTTP status reason phrase (e.g., "Bad Request")
 * @param code Domain error code (e.g., "VALIDATION_FAILED", "UNAUTHORIZED")
 * @param message Human-readable, client-safe error message
 * @param path Requested resource URI path
 * @param details Optional field-level error details (for validation failures)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    List<FieldErrorDetail> details
) {
    public ErrorResponse(Instant timestamp, int status, String error, String code, String message, String path) {
        this(timestamp, status, error, code, message, path, null);
    }

    public record FieldErrorDetail(
        String field,
        String issue
    ) {}
}
