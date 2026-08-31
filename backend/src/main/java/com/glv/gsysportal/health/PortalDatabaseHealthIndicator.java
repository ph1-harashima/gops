package com.glv.gsysportal.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §2/§3): {@code /actuator/health} contributor for the Portal PostgreSQL DB.
 *
 * <p>Written by hand and explicitly {@code @Qualifier}'d to the Portal
 * (Primary) DataSource, rather than relying on Spring Boot's automatic
 * multi-DataSource health indicator (which would ambiguously register a
 * second contributor for the Legacy DataSource too - this application has
 * two DataSource beans, and Spring Boot's default DB health auto-config
 * does not distinguish "the one that should gate Application health" from
 * "the READ ONLY downstream dependency that should not", Production
 * Readiness Audit §3's explicit "Legacy unavailable must not = Application
 * DOWN" requirement). {@code management.health.db.enabled=false} in
 * application.yml disables the automatic one entirely; this class is the
 * only DB contributor to the root {@code /actuator/health} aggregate.
 *
 * <p>Legacy DB is intentionally NOT a {@link HealthIndicator} at all - see
 * {@link LegacyHealthCheckService} and {@code HealthController}, exposed as
 * a separate {@code GET /api/health/legacy} endpoint that never feeds into
 * this aggregate, so its status can never drag Application health DOWN.
 * Whether Legacy-down SHOULD affect overall system health is a Production
 * Operation decision this Phase does not make (§3's explicit instruction).
 */
@Component("portalDb")
public class PortalDatabaseHealthIndicator implements HealthIndicator {

    private static final int VALIDATION_TIMEOUT_SECONDS = 3;

    private final DataSource portalDataSource;

    public PortalDatabaseHealthIndicator(@Qualifier("prototypeDataSource") DataSource portalDataSource) {
        this.portalDataSource = portalDataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = portalDataSource.getConnection()) {
            if (connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                // Deliberately no URL/username/host/driver details in the
                // Health body (Production Readiness Audit §4 - Health
                // Endpoint must never leak connection info/Secrets).
                return Health.up().build();
            }
            return Health.down().build();
        } catch (SQLException e) {
            // Same reasoning: no exception message/stack trace in the body.
            return Health.down().build();
        }
    }
}
