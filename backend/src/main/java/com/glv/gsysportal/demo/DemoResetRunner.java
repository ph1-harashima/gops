package com.glv.gsysportal.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.util.Set;

/**
 * Demo Reset (implementation instructions Step 5 5章): re-initialize the
 * Prototype's business-workflow data before a demo rehearsal/run, so the
 * Core Workflow can be walked through repeatedly without accumulating stale
 * Draft/Order/Attention rows from prior runs. {@code portal_user} is
 * deliberately NOT touched (demo accounts must keep working).
 *
 * <p>NEVER exposed as a Backend API - this is a {@link CommandLineRunner}
 * that is a strict no-op unless explicitly opted into via
 * {@code app.demo-reset.enabled=true}, which is never set in
 * {@code application.yml} and must be passed on the command line, e.g.:
 * <pre>
 *   mvn spring-boot:run -Dspring-boot.run.profiles=local \
 *       -Dspring-boot.run.arguments=--app.demo-reset.enabled=true
 * </pre>
 * (see {@code backend/demo-reset.sh}). The process exits immediately after
 * a successful reset - this is a one-shot CLI operation, not a server mode.
 *
 * <p><b>Defense in depth</b>: by the time this runner executes, Spring Boot
 * has already completed {@link com.glv.gsysportal.safety.SafetyGuardEnvironmentPostProcessor},
 * which refuses to even start the application unless the active Profile is
 * one of local/demo/test AND both the Legacy and Prototype JDBC URLs point
 * at allowlisted localhost/Docker-service hosts and database names - so a
 * misconfigured Reset invocation against a non-local environment cannot
 * reach this code at all. This class additionally re-validates the
 * Prototype connection's actual host and database name at runtime,
 * independent of that startup-time check, before issuing any DELETE/TRUNCATE.
 * This never touches the Legacy Demo MySQL Seed Schema in any way.
 */
@Component
public class DemoResetRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoResetRunner.class);

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "prototype-postgres");
    private static final String ALLOWED_PROTOTYPE_DB_NAME = "gsys_portal";

    private final boolean enabled;
    private final DataSource prototypeDataSource;
    private final JdbcTemplate prototypeJdbc;

    public DemoResetRunner(@Value("${app.demo-reset.enabled:false}") boolean enabled,
                            @Qualifier("prototypeDataSource") DataSource prototypeDataSource) {
        this.enabled = enabled;
        this.prototypeDataSource = prototypeDataSource;
        this.prototypeJdbc = new JdbcTemplate(prototypeDataSource);
    }

    @Override
    public void run(String... args) throws Exception {
        if (!enabled) {
            return; // strict no-op on every normal application startup/demo run
        }

        verifyPrototypeConnectionIsLocal();

        log.warn("=== DEMO RESET: about to TRUNCATE Prototype business-data tables "
                + "(portal_order, portal_order_detail, supplier_response, supplier_response_detail, "
                + "order_attention, audit_event). portal_user is preserved. Target: {} ===",
                prototypeDataSource.getConnection().getMetaData().getURL());

        prototypeJdbc.execute(
                "TRUNCATE TABLE audit_event, order_attention, supplier_response_detail, "
                        + "supplier_response, portal_order_detail, portal_order RESTART IDENTITY");
        prototypeJdbc.execute("ALTER SEQUENCE prototype_po_no_seq RESTART WITH 1");

        log.warn("=== DEMO RESET: complete. portal_user accounts unchanged. Exiting. ===");
        System.exit(0);
    }

    private void verifyPrototypeConnectionIsLocal() throws Exception {
        try (Connection connection = prototypeDataSource.getConnection()) {
            validateJdbcUrl(connection.getMetaData().getURL());
        }
    }

    /** Extracted as a pure, Connection-free static method so it can be unit
     * tested directly (the instance path deliberately calls
     * {@link System#exit}, which a normal in-process JUnit run must never
     * trigger). Mirrors {@code SafetyGuardEnvironmentPostProcessor}'s
     * URI-based (not substring/contains) parsing approach. */
    static void validateJdbcUrl(String jdbcUrl) {
        String withoutPrefix = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring("jdbc:".length()) : jdbcUrl;
        URI uri;
        try {
            uri = new URI(withoutPrefix);
        } catch (Exception e) {
            throw new IllegalStateException("Demo Reset refused: could not parse Prototype JDBC URL: " + jdbcUrl, e);
        }
        String host = uri.getHost();
        String path = uri.getPath();
        String databaseName = (path != null && path.startsWith("/")) ? path.substring(1) : path;
        if (databaseName != null && databaseName.contains("?")) {
            databaseName = databaseName.substring(0, databaseName.indexOf('?'));
        }

        if (host == null || !ALLOWED_HOSTS.contains(host)) {
            throw new IllegalStateException(
                    "Demo Reset refused: Prototype host '" + host + "' is not on the allowlist " + ALLOWED_HOSTS
                            + " (parsed from " + jdbcUrl + "). Reset itself is refused.");
        }
        if (!ALLOWED_PROTOTYPE_DB_NAME.equals(databaseName)) {
            throw new IllegalStateException(
                    "Demo Reset refused: Prototype database name '" + databaseName
                            + "' is not '" + ALLOWED_PROTOTYPE_DB_NAME + "' (parsed from " + jdbcUrl
                            + "). Reset itself is refused.");
        }
    }
}
