package com.groomai.common.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Public health check controller.
 * Exposes GET /api/v1/health returning basic service status without leaking infrastructure details.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private static final String SERVICE_NAME = "groomai-backend";
    private static final String STATUS_UP = "UP";

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> getHealth() {
        return ResponseEntity.ok(new HealthResponse(STATUS_UP, SERVICE_NAME, Instant.now()));
    }
}
