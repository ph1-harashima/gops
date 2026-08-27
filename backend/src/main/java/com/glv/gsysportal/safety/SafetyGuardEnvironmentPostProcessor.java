package com.glv.gsysportal.safety;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
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
 *       the real Legacy production schema name) and the Prototype JDBC URL's
 *       database name must be in {@link #ALLOWED_PROTOTYPE_DB_NAMES}.</li>
 * </ol>
 *
 * Registered via {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}.
 */
public class SafetyGuardEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final Set<String> ALLOWED_PROFILES = Set.of("local", "demo", "test");

    // localhost/127.0.0.1 for host-run Application; service names match
    // docker-compose.yml (legacy-demo-mysql, prototype-postgres) for the case
    // where the Application itself later runs inside the Compose network.
    static final Set<String> ALLOWED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "legacy-demo-mysql", "prototype-postgres"
    );

    static final Set<String> ALLOWED_LEGACY_DB_NAMES = Set.of("legacy_demo");
    static final Set<String> ALLOWED_PROTOTYPE_DB_NAMES = Set.of("gsys_portal");

    private static final String LEGACY_JDBC_URL_PROPERTY = "app.legacy.datasource.jdbc-url";
    private static final String PROTOTYPE_JDBC_URL_PROPERTY = "app.prototype.datasource.jdbc-url";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> violations = new ArrayList<>();

        validateProfiles(environment.getActiveProfiles(), violations);
        validateDataSource("Legacy", environment.getProperty(LEGACY_JDBC_URL_PROPERTY),
                ALLOWED_LEGACY_DB_NAMES, violations);
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
