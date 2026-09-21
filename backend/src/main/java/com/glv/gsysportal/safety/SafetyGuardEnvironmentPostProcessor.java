package com.glv.gsysportal.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CRITICAL SAFETY GATE - Production Impact Prevention.
 *
 * This is a local-only Prototype. This guard runs during Spring Boot's
 * environment post-processing phase - BEFORE any {@code DataSource} bean is
 * created and BEFORE any network connection is possible - and throws to abort
 * {@code SpringApplication.run()} if any of the following are not satisfied:
 *
 * <ol>
 *   <li><b>Profile Guard</b>: at least one active Spring profile must be
 *       explicitly set, and every active profile must be in
 *       {@link #ALLOWED_PROFILES}. The application NEVER starts under the
 *       default profile (no profile specified) or under
 *       production/prod/staging/any unrecognized profile.</li>
 *   <li><b>Host Allowlist Guard</b>: both the Legacy and Prototype JDBC URL
 *       hosts (parsed via {@link URI}, not a substring/contains check) must
 *       be in {@link #ALLOWED_HOSTS}.</li>
 *   <li><b>Database Name Guard</b>: the Legacy JDBC URL's database name must
 *       be in {@link #ALLOWED_LEGACY_DB_NAMES} (explicitly NEVER {@code goo},
 *       the real Legacy production schema name - see
 *       {@link #HARD_DENIED_LEGACY_DB_NAMES}, checked unconditionally, before
 *       and independent of any allowlist) and the Prototype JDBC URL's
 *       database name must be in {@link #ALLOWED_PROTOTYPE_DB_NAMES}.</li>
 * </ol>
 *
 * <p><b>Snapshot Validation Gate</b> (Stage 5A design, docs/real-data-audit/
 * gops-stage5a-schema-case-and-safety-gate-audit.md §11; implemented Stage
 * 5B, docs/real-data-audit/gops-stage5b-production-like-snapshot-environment.md):
 * a narrow, explicit-opt-in exception that lets a Controlled Snapshot
 * Validation session point the Legacy datasource at one additional,
 * exactly-named database - WITHOUT any source edit - by requiring BOTH of
 * the following simultaneously:
 * <ul>
 *   <li>the {@value #SNAPSHOT_VALIDATION_PROFILE} Spring profile is active
 *       (in addition to one of local/demo/test - {@code @Profile({"local",
 *       "demo","test"})} beans elsewhere in this codebase still require one
 *       of those three to also be active); and</li>
 *   <li>the {@value #SNAPSHOT_VALIDATION_DB_NAME_PROPERTY} environment
 *       variable is set to the exact (not pattern/wildcard-matched) database
 *       name to allow.</li>
 * </ul>
 * Neither alone grants anything - the base {@link #ALLOWED_LEGACY_DB_NAMES}
 * ({@code legacy_demo}) is unconditionally, always still allowed, and every
 * other Safety Gate check (host allowlist, Prototype guard, hard-denied
 * names) is completely unaffected. {@link #HARD_DENIED_LEGACY_DB_NAMES} is
 * checked before this exception is even evaluated, so no value of the
 * property can ever allow a hard-denied name through. Whenever this
 * exception actually engages, a {@code WARN}-level log line is emitted
 * (never hidden, never at {@code DEBUG}) - see {@link #postProcessEnvironment}.
 *
 * Registered via {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}.
 */
public class SafetyGuardEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SafetyGuardEnvironmentPostProcessor.class);

    static final String SNAPSHOT_VALIDATION_PROFILE = "snapshot-validation";
    static final String SNAPSHOT_VALIDATION_DB_NAME_PROPERTY = "GOPS_SNAPSHOT_VALIDATION_ALLOWED_DB_NAME";

    static final Set<String> ALLOWED_PROFILES = Set.of("local", "demo", "test", SNAPSHOT_VALIDATION_PROFILE);

    // localhost/127.0.0.1 for host-run Application; service names match
    // docker-compose.yml (legacy-demo-mysql, prototype-postgres) for the case
    // where the Application itself later runs inside the Compose network.
    // A Controlled Snapshot Validation container (Stage 5B) is also
    // published on localhost (a different port), so no new host entry is
    // needed for that use case.
    static final Set<String> ALLOWED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "legacy-demo-mysql", "prototype-postgres"
    );

    static final Set<String> ALLOWED_LEGACY_DB_NAMES = Set.of("legacy_demo");
    static final Set<String> ALLOWED_PROTOTYPE_DB_NAMES = Set.of("gsys_portal");

    // Permanent, unconditional deny list for the Legacy database name -
    // checked BEFORE, and independently of, any allowlist (base or
    // Snapshot-Validation-expanded). No configuration value, property, or
    // profile can ever cause a name on this list to be allowed. "goo" is
    // the real Legacy Production schema name (Stage 3B/5A).
    static final Set<String> HARD_DENIED_LEGACY_DB_NAMES = Set.of("goo");

    private static final String LEGACY_JDBC_URL_PROPERTY = "app.legacy.datasource.jdbc-url";
    private static final String PROTOTYPE_JDBC_URL_PROPERTY = "app.prototype.datasource.jdbc-url";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> violations = new ArrayList<>();

        String[] activeProfiles = environment.getActiveProfiles();
        validateProfiles(activeProfiles, violations);

        String configuredSnapshotDbName = environment.getProperty(SNAPSHOT_VALIDATION_DB_NAME_PROPERTY);
        Set<String> effectiveLegacyDbNames = resolveEffectiveLegacyDbNames(activeProfiles, configuredSnapshotDbName);

        if (effectiveLegacyDbNames.size() > ALLOWED_LEGACY_DB_NAMES.size()) {
            // The exception actually engaged (profile + property both present
            // and the configured name was not hard-denied) - log at WARN
            // regardless of whether the Datasource Guard below ultimately
            // still fails for some other reason (e.g. host), since the
            // exception itself is the security-relevant event to surface.
            // As with the violation message below, the logging framework may
            // not be fully initialized this early (confirmed Stage 5B: a
            // plain log.warn() here was silently lost under
            // spring-boot:run) - print to stdout too so this is never
            // silently missed.
            String snapshotModeMessage = "SNAPSHOT VALIDATION MODE ACTIVE - Legacy datasource database name '"
                    + configuredSnapshotDbName + "' is allowed for this run ONLY, via explicit opt-in ("
                    + SNAPSHOT_VALIDATION_PROFILE + " profile + " + SNAPSHOT_VALIDATION_DB_NAME_PROPERTY
                    + " environment variable). READ-ONLY validation only - this must NEVER be used to connect "
                    + "to real Production or UAT. The real Production schema name ('goo') remains permanently "
                    + "denied regardless of this or any other configuration.";
            log.warn(snapshotModeMessage);
            System.out.println(snapshotModeMessage);
        }

        validateDataSource("Legacy", environment.getProperty(LEGACY_JDBC_URL_PROPERTY),
                effectiveLegacyDbNames, violations);
        validateDataSource("Prototype", environment.getProperty(PROTOTYPE_JDBC_URL_PROPERTY),
                ALLOWED_PROTOTYPE_DB_NAMES, violations);

        if (!violations.isEmpty()) {
            String message = "SAFETY GUARD VIOLATION - refusing to start (local-Prototype-only application):\n"
                    + " - " + String.join("\n - ", violations);
            // Logging framework may not be fully initialized this early - print to stderr too
            // so the reason for startup failure is never silently lost.
            System.err.println(message);
            throw new SafetyGuardViolationException(message);
        }
    }

    static void validateProfiles(String[] activeProfiles, List<String> violations) {
        if (activeProfiles == null || activeProfiles.length == 0) {
            violations.add("Profile Guard: no active Spring profile set (default profile). "
                    + "Must explicitly activate one of " + ALLOWED_PROFILES + ".");
            return;
        }
        for (String profile : activeProfiles) {
            if (!ALLOWED_PROFILES.contains(profile)) {
                violations.add("Profile Guard: active profile '" + profile
                        + "' is not on the allowlist " + ALLOWED_PROFILES + ".");
            }
        }
    }

    /**
     * Snapshot Validation Gate (see class Javadoc). Returns
     * {@link #ALLOWED_LEGACY_DB_NAMES} unchanged unless BOTH the
     * {@value #SNAPSHOT_VALIDATION_PROFILE} profile is active AND
     * {@code configuredSnapshotDbName} is a non-blank, non-hard-denied
     * value - in which case that exact name (and only that name) is added,
     * on top of (never instead of) the base allowlist. No wildcard/pattern
     * matching anywhere in this method - {@code Set} membership and
     * {@code String.equals} only.
     */
    static Set<String> resolveEffectiveLegacyDbNames(String[] activeProfiles, String configuredSnapshotDbName) {
        if (!containsProfile(activeProfiles, SNAPSHOT_VALIDATION_PROFILE)) {
            return ALLOWED_LEGACY_DB_NAMES;
        }
        if (configuredSnapshotDbName == null || configuredSnapshotDbName.isBlank()) {
            return ALLOWED_LEGACY_DB_NAMES;
        }
        if (HARD_DENIED_LEGACY_DB_NAMES.contains(configuredSnapshotDbName)) {
            return ALLOWED_LEGACY_DB_NAMES;
        }
        Set<String> expanded = new HashSet<>(ALLOWED_LEGACY_DB_NAMES);
        expanded.add(configuredSnapshotDbName);
        return Set.copyOf(expanded);
    }

    private static boolean containsProfile(String[] activeProfiles, String profile) {
        if (activeProfiles == null) {
            return false;
        }
        for (String p : activeProfiles) {
            if (profile.equals(p)) {
                return true;
            }
        }
        return false;
    }

    static void validateDataSource(String label, String jdbcUrl, Set<String> allowedDbNames, List<String> violations) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            violations.add(label + " Datasource Guard: JDBC URL is not configured.");
            return;
        }

        ParsedJdbcUrl parsed;
        try {
            parsed = parseJdbcUrl(jdbcUrl);
        } catch (URISyntaxException e) {
            violations.add(label + " Datasource Guard: JDBC URL could not be parsed: " + jdbcUrl);
            return;
        }

        if (parsed.host() == null || !ALLOWED_HOSTS.contains(parsed.host())) {
            violations.add(label + " Datasource Guard: host '" + parsed.host()
                    + "' is not on the allowlist " + ALLOWED_HOSTS + " (parsed from " + jdbcUrl + ").");
        }

        // Permanent hard-deny check - independent of, and prior to, the
        // allowedDbNames membership check below. Applies to every label
        // (only Legacy has entries in HARD_DENIED_LEGACY_DB_NAMES today, but
        // this is not label-conditional - see class Javadoc).
        if (parsed.databaseName() != null && HARD_DENIED_LEGACY_DB_NAMES.contains(parsed.databaseName())) {
            violations.add(label + " Datasource Guard: database name '" + parsed.databaseName()
                    + "' is permanently denied (real Production schema name) and can never be allowed, "
                    + "regardless of any allowlist or configuration (parsed from " + jdbcUrl + ").");
            return;
        }

        if (parsed.databaseName() == null || !allowedDbNames.contains(parsed.databaseName())) {
            violations.add(label + " Datasource Guard: database name '" + parsed.databaseName()
                    + "' is not on the allowlist " + allowedDbNames + " (parsed from " + jdbcUrl + ").");
        }
    }

    /**
     * Parses a {@code jdbc:<subprotocol>://host:port/database?params} URL into
     * host and database name using {@link URI}, NOT a substring/contains
     * check, per the Safety Gate requirement.
     */
    static ParsedJdbcUrl parseJdbcUrl(String jdbcUrl) throws URISyntaxException {
        String withoutJdbcPrefix = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring("jdbc:".length()) : jdbcUrl;
        URI uri = new URI(withoutJdbcPrefix);
        String host = uri.getHost();
        String path = uri.getPath(); // e.g. "/legacy_demo"
        String databaseName = (path != null && path.startsWith("/")) ? path.substring(1) : path;
        // Guard against a trailing slash or nested path segment being mistaken for the DB name.
        if (databaseName != null && databaseName.contains("/")) {
            databaseName = databaseName.substring(0, databaseName.indexOf('/'));
        }
        return new ParsedJdbcUrl(host, databaseName);
    }

    record ParsedJdbcUrl(String host, String databaseName) {
    }
}
