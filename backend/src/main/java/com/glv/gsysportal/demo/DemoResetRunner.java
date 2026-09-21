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
 *
 * <p><b>Phase 1 Final Cleanup (Test Data Lifecycle)</b>: {@link #includeTestMasterData}
 * (opt-in, {@code app.demo-reset.include-test-master-data=true}, see
 * {@code demo-reset.sh --include-test-master-data}) additionally runs
 * {@link #cleanupTestMasterData()} - a physical DELETE of E2E-generated
 * Portal Master rows, restricted to patterns confirmed (by direct audit of
 * every row-creation path, docs/gops-phase1-final-cleanup-report.md) to be
 * 100% Test-only: {@code supplier_contact} rows whose email ends in
 * {@code @example.com} (never used by any migration/seed data) and
 * {@code mail_template} rows literally named {@code "Follow-up E2E Template %"}
 * or (Final E2E Remediation, docs/gops-final-e2e-failure-root-cause-analysis.md
 * §12-13: every E2E spec that dynamically creates a Mail Template now names
 * it with this prefix, closing the "majority of mail_template rows remain
 * ambiguous" gap the RCA doc identified) {@code "E2E %"}.
 * Both predicates also require {@code is_active = false}, so an Active row -
 * Demo Business Master or otherwise - is never touched (Regression C).
 * {@code manufacturer_channel}, {@code supplier_region_classification}, and
 * every {@code mail_template} row not matching one of the two markers above
 * are deliberately left alone: no content-based marker distinguishes their
 * E2E-created rows from genuine Demo Master data today, and
 * "曖昧な条件によるDELETEは禁止" (100%識別できない場合：削除しない) is an
 * absolute rule here, not a preference - see Known Limitations in the
 * Freeze doc for the follow-up. */
@Component
public class DemoResetRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoResetRunner.class);

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "prototype-postgres");
    private static final String ALLOWED_PROTOTYPE_DB_NAME = "gsys_portal";

    private final boolean enabled;
    private final boolean includeTestMasterData;
    private final DataSource prototypeDataSource;
    private final JdbcTemplate prototypeJdbc;

    public DemoResetRunner(@Value("${app.demo-reset.enabled:false}") boolean enabled,
                            @Value("${app.demo-reset.include-test-master-data:false}") boolean includeTestMasterData,
                            @Qualifier("prototypeDataSource") DataSource prototypeDataSource) {
        this.enabled = enabled;
        this.includeTestMasterData = includeTestMasterData;
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
                + "(portal_order, portal_order_detail, portal_order_revision, portal_order_revision_detail, "
                + "supplier_response, supplier_response_detail, order_attention, audit_event, "
                + "official_po_integration_request, order_email, follow_up_case, legacy_po_baseline, "
                + "price_change_set, price_change_set_detail, idempotent_operation). "
                + "portal_user is preserved. Target: {} ===",
                prototypeDataSource.getConnection().getMetaData().getURL());

        // official_po_integration_request (7-C2A) and portal_order_revision/
        // portal_order_revision_detail (7-C5, supplier_response now FK-references
        // portal_order_revision too) all FK-reference portal_order, so they
        // must be included in the same TRUNCATE statement (Postgres refuses
        // to truncate a table still referenced by an untouched FK from
        // another table). follow_up_case (7-C7A) and portal_order now
        // FK-reference EACH OTHER (portal_order.source_follow_up_case_id ->
        // follow_up_case, follow_up_case.portal_order_id -> portal_order) -
        // Postgres TRUNCATE resolves this fine as long as both are listed in
        // the SAME statement, which they are here. legacy_po_baseline (7-C6)
        // FK-references portal_order the same simple way official_po_integration_request
        // does, so it just joins the same list. price_change_set/
        // price_change_set_detail (8-B) are a SEPARATE aggregate root with no
        // FK to portal_order at all, but audit_event.price_change_set_id now
        // FK-references price_change_set (V16), so price_change_set must be
        // in this SAME statement as audit_event for the same reason as every
        // other table here. idempotent_operation (8-L) has no FK to anything
        // (a deliberately standalone Technical table) - included here anyway
        // rather than exempted Master-data-style like supplier_contact/
        // mail_template, since it is closer in kind to audit_event/
        // official_po_integration_request (a per-attempt technical record
        // tied to a Demo run, not standing config) - so stale claims from a
        // prior Demo session never block a fresh one.
        prototypeJdbc.execute(
                "TRUNCATE TABLE audit_event, order_attention, supplier_response_detail, "
                        + "supplier_response, official_po_integration_request, order_email, portal_order_revision_detail, "
                        + "portal_order_revision, follow_up_case, legacy_po_baseline, portal_order_detail, portal_order, "
                        + "price_change_set_detail, price_change_set, idempotent_operation RESTART IDENTITY");
        prototypeJdbc.execute("ALTER SEQUENCE prototype_po_no_seq RESTART WITH 1");

        if (includeTestMasterData) {
            cleanupTestMasterData();
        }

        log.warn("=== DEMO RESET: complete. portal_user accounts unchanged. Exiting. ===");
        System.exit(0);
    }

    /** Package-private (not {@code private}) so an integration test can call
     * it directly against a real Prototype connection without going through
     * {@link #run}, which unconditionally calls {@link System#exit} on
     * success. Re-validates the connection itself (defense in depth,
     * independent of {@link #run}'s own call) since this is a second,
     * independently reachable entry point into the same destructive
     * capability. See the class Javadoc for exactly which rows this deletes
     * and why the rest are deliberately left alone. */
    void cleanupTestMasterData() throws Exception {
        verifyPrototypeConnectionIsLocal();

        int contactsDeleted = prototypeJdbc.update(
                "DELETE FROM supplier_contact WHERE is_active = false AND email LIKE '%@example.com'");
        int templatesDeleted = prototypeJdbc.update(
                "DELETE FROM mail_template WHERE is_active = false "
                        + "AND (template_name LIKE 'Follow-up E2E Template %' OR template_name LIKE 'E2E %')");

        log.warn("=== TEST MASTER DATA CLEANUP: deleted {} supplier_contact row(s) "
                        + "(is_active=false AND email LIKE '%@example.com') and {} mail_template row(s) "
                        + "(is_active=false AND template_name LIKE 'Follow-up E2E Template %' OR 'E2E %'). "
                        + "manufacturer_channel, supplier_region_classification, official_po_short_code, and every "
                        + "other mail_template row are left untouched - no reliable content-based Test marker "
                        + "exists for them yet. ===",
                contactsDeleted, templatesDeleted);
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
