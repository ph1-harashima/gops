package com.glv.gsysportal.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §2/§3/§4). {@code GET /api/health/legacy} - the Legacy G-SYS Adapter
 * dependency status, reported independently of {@code /actuator/health}
 * (Application + Portal DB) so a Legacy outage is observable without ever
 * flipping Application health to DOWN (§3's explicit requirement; whether
 * that SHOULD happen is a Production Operation decision left open).
 *
 * <p>Body is deliberately minimal ({@code {"status": "UP"|"DOWN"}}) - no
 * JDBC URL, username, host, or exception detail (§4).
 */
@RestController
public class HealthController {

    private final LegacyHealthCheckService legacyHealthCheckService;

    public HealthController(LegacyHealthCheckService legacyHealthCheckService) {
        this.legacyHealthCheckService = legacyHealthCheckService;
    }

    @GetMapping("/api/health/legacy")
    public ResponseEntity<Map<String, String>> legacyHealth() {
        boolean up = legacyHealthCheckService.isUp();
        Map<String, String> body = Map.of("status", up ? "UP" : "DOWN");
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
