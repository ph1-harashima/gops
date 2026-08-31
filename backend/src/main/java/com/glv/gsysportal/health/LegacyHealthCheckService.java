package com.glv.gsysportal.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §2/§3): Legacy G-SYS Adapter dependency health, deliberately NOT a Spring
 * Boot Actuator {@code HealthIndicator} - see
 * {@link PortalDatabaseHealthIndicator}'s Javadoc for why. Consumed only by
 * {@code HealthController}'s {@code GET /api/health/legacy}, which never
 * feeds into {@code /actuator/health}'s aggregate status.
 *
 * <p>READ ONLY: opens a connection from the existing {@code legacyDataSource}
 * pool and validates it (JDBC {@code isValid}) - no query is issued against
 * any Legacy table, so this cannot be mistaken for a Business data check.
 */
@Service
public class LegacyHealthCheckService {

    private static final int VALIDATION_TIMEOUT_SECONDS = 3;

    private final DataSource legacyDataSource;

    public LegacyHealthCheckService(@Qualifier("legacyDataSource") DataSource legacyDataSource) {
        this.legacyDataSource = legacyDataSource;
    }

    public boolean isUp() {
        try (Connection connection = legacyDataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            return false;
        }
    }
}
