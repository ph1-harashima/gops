package com.glv.gsysportal.safety;

import com.glv.gsysportal.GsysPortalApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end proof (0.6/0.7) that a Safety Gate violation actually fails
 * {@code ApplicationContext} startup, not merely logs a warning. Each bad
 * case is expected to throw before any DataSource bean attempts a network
 * connection, so these tests do not require Docker to be running.
 */
class SafetyGuardIntegrationTest {

    @Test
    void applicationContextFailsToStart_whenLegacySchemaIsGoo() {
        assertThrows(SafetyGuardViolationException.class, () ->
            boot("test",
                "app.legacy.datasource.jdbc-url=jdbc:mysql://localhost:33061/goo",
                "app.prototype.datasource.jdbc-url=jdbc:postgresql://localhost:54321/gsys_portal"
            )
        );
    }

    @Test
    void applicationContextFailsToStart_whenLegacyHostIsExternal() {
        assertThrows(SafetyGuardViolationException.class, () ->
            boot("test",
                "app.legacy.datasource.jdbc-url=jdbc:mysql://production.example.com:3306/legacy_demo",
                "app.prototype.datasource.jdbc-url=jdbc:postgresql://localhost:54321/gsys_portal"
            )
        );
    }

    @Test
    void applicationContextFailsToStart_whenPrototypeDbNameIsUnknown() {
        assertThrows(SafetyGuardViolationException.class, () ->
            boot("test",
                "app.legacy.datasource.jdbc-url=jdbc:mysql://localhost:33061/legacy_demo",
                "app.prototype.datasource.jdbc-url=jdbc:postgresql://localhost:54321/some_unknown_db"
            )
        );
    }

    @Test
    void applicationContextFailsToStart_whenProfileIsProduction() {
        assertThrows(SafetyGuardViolationException.class, () ->
            boot("production",
                "app.legacy.datasource.jdbc-url=jdbc:mysql://localhost:33061/legacy_demo",
                "app.prototype.datasource.jdbc-url=jdbc:postgresql://localhost:54321/gsys_portal"
            )
        );
    }

    @Test
    void applicationContextFailsToStart_whenNoProfileIsActive() {
        assertThrows(SafetyGuardViolationException.class, () ->
            boot(null,
                "app.legacy.datasource.jdbc-url=jdbc:mysql://localhost:33061/legacy_demo",
                "app.prototype.datasource.jdbc-url=jdbc:postgresql://localhost:54321/gsys_portal"
            )
        );
    }

    /**
     * Stage 5B Snapshot Validation Gate (docs/real-data-audit/
     * gops-stage5b-production-like-snapshot-environment.md §5/§6): the
     * snapshot-validation profile alone, without the exact-match
     * GOPS_SNAPSHOT_VALIDATION_ALLOWED_DB_NAME env var, must grant nothing -
     * still fails before any DataSource connection attempt, so this does not
     * require Docker.
     */
    @Test
    void applicationContextFailsToStart_whenSnapshotProfileActiveButDbPropertyMissing() {
        assertThrows(SafetyGuardViolationException.class, () ->
            boot("local,snapshot-validation",
                "app.legacy.datasource.jdbc-url=jdbc:mysql://localhost:33199/goo_prod_snapshot_20260916",
                "app.prototype.datasource.jdbc-url=jdbc:postgresql://localhost:54321/gsys_portal"
            )
        );
    }

    /**
     * The real Legacy Production schema name ("goo") must remain permanently
     * denied even when the snapshot-validation profile is active and an
     * attacker/misconfiguration sets the exact-match property to "goo"
     * itself - proven end-to-end, not just at the static-method level (see
     * SafetyGuardEnvironmentPostProcessorTest.goo_isNeverAllowed_*).
     */
    @Test
    void applicationContextFailsToStart_whenSnapshotProfileActiveAndPropertySetToGoo() {
        assertThrows(SafetyGuardViolationException.class, () ->
            boot("local,snapshot-validation",
                "app.legacy.datasource.jdbc-url=jdbc:mysql://localhost:3306/goo",
                "app.prototype.datasource.jdbc-url=jdbc:postgresql://localhost:54321/gsys_portal",
                "GOPS_SNAPSHOT_VALIDATION_ALLOWED_DB_NAME=goo"
            )
        );
    }

    /**
     * Boots the real application (minus the web server, to keep the test
     * fast) with the given profile and property overrides, closing the
     * context immediately if it does start.
     *
     * IMPORTANT: {@code SpringApplicationBuilder.properties(...)} adds values
     * as the LOWEST-precedence "defaultProperties" source - lower than
     * application.yml - so it CANNOT override app.legacy/prototype.datasource
     * values for this test's purposes (verified: using it made every "bad"
     * case silently fall back to the real application.yml value and pass the
     * guard). System properties sit above application.yml in Spring Boot's
     * property resolution order, so we use those instead, restoring/clearing
     * them afterward for test isolation.
     */
    private static void boot(String profile, String... properties) {
        List<String> keysToClear = new ArrayList<>();
        try {
            for (String kv : properties) {
                int eq = kv.indexOf('=');
                String key = kv.substring(0, eq);
                String value = kv.substring(eq + 1);
                System.setProperty(key, value);
                keysToClear.add(key);
            }
            if (profile != null) {
                System.setProperty("spring.profiles.active", profile);
                keysToClear.add("spring.profiles.active");
            } else {
                System.clearProperty("spring.profiles.active");
            }

            SpringApplicationBuilder builder = new SpringApplicationBuilder(GsysPortalApplication.class)
                    .web(WebApplicationType.NONE);
            ConfigurableApplicationContext context = null;
            try {
                context = builder.run();
            } finally {
                if (context != null) {
                    context.close();
                }
            }
        } finally {
            keysToClear.forEach(System::clearProperty);
        }
    }
}
