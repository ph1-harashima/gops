package com.glv.gsysportal.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fast unit-level matrix covering the Safety Gate's validation logic directly
 * (no Spring context boot). See {@link SafetyGuardIntegrationTest} for the
 * slower end-to-end proof that a violation actually fails
 * {@code ApplicationContext} startup.
 */
class SafetyGuardEnvironmentPostProcessorTest {

    // ------------------------------------------------------------------
    // 0.6 Safety Guard Tests - PASS cases
    // ------------------------------------------------------------------

    @Test
    void legacyLocalhostAndLegacyDemoSchema_pass() throws URISyntaxException {
        assertNoViolations("Legacy",
                "jdbc:mysql://localhost:33061/legacy_demo?useSSL=false&characterEncoding=UTF-8",
                SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES);
    }

    @Test
    void legacyDockerServiceNameAndLegacyDemoSchema_pass() throws URISyntaxException {
        assertNoViolations("Legacy",
                "jdbc:mysql://legacy-demo-mysql:3306/legacy_demo?useSSL=false",
                SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES);
    }

    @Test
    void prototypeLocalhostAndGsysPortalDb_pass() throws URISyntaxException {
        assertNoViolations("Prototype",
                "jdbc:postgresql://localhost:54321/gsys_portal",
                SafetyGuardEnvironmentPostProcessor.ALLOWED_PROTOTYPE_DB_NAMES);
    }

    @Test
    void prototypeDockerServiceNameAndGsysPortalDb_pass() throws URISyntaxException {
        assertNoViolations("Prototype",
                "jdbc:postgresql://prototype-postgres:5432/gsys_portal",
                SafetyGuardEnvironmentPostProcessor.ALLOWED_PROTOTYPE_DB_NAMES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "demo", "test", "snapshot-validation"})
    void allowedProfile_passes(String profile) {
        List<String> violations = new ArrayList<>();
        SafetyGuardEnvironmentPostProcessor.validateProfiles(new String[]{profile}, violations);
        assertTrue(violations.isEmpty(), "Expected no violations for profile: " + profile);
    }

    // ------------------------------------------------------------------
    // 0.6 Safety Guard Tests - FAIL cases
    // ------------------------------------------------------------------

    @Test
    void externalIp_fails() throws URISyntaxException {
        assertHasViolation("Legacy",
                "jdbc:mysql://203.0.113.55:3306/legacy_demo",
                SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES,
                "host");
    }

    @Test
    void externalHostname_fails() throws URISyntaxException {
        assertHasViolation("Legacy",
                "jdbc:mysql://db.some-external-vendor.net:3306/legacy_demo",
                SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES,
                "host");
    }

    @Test
    void productionExampleComHostname_fails() throws URISyntaxException {
        assertHasViolation("Legacy",
                "jdbc:mysql://production.example.com:3306/legacy_demo",
                SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES,
                "host");
    }

    @Test
    void legacySchemaGoo_fails() throws URISyntaxException {
        // The real Legacy production schema name must NEVER be allowed, even on localhost.
        assertHasViolation("Legacy",
                "jdbc:mysql://localhost:3306/goo",
                SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES,
                "database name");
    }

    @Test
    void legacySchemaGoo_failsEvenOnDockerServiceHost() throws URISyntaxException {
        assertHasViolation("Legacy",
                "jdbc:mysql://legacy-demo-mysql:3306/goo",
                SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES,
                "database name");
    }

    @Test
    void prototypeUnknownDbName_fails() throws URISyntaxException {
        assertHasViolation("Prototype",
                "jdbc:postgresql://localhost:54321/some_other_db",
                SafetyGuardEnvironmentPostProcessor.ALLOWED_PROTOTYPE_DB_NAMES,
                "database name");
    }

    @Test
    void missingJdbcUrl_fails() {
        List<String> violations = new ArrayList<>();
        SafetyGuardEnvironmentPostProcessor.validateDataSource("Legacy", null,
                SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES, violations);
        assertEquals(1, violations.size());
    }

    @Test
    void productionProfile_fails() {
        List<String> violations = new ArrayList<>();
        SafetyGuardEnvironmentPostProcessor.validateProfiles(new String[]{"production"}, violations);
        assertTrue(violations.stream().anyMatch(v -> v.contains("production")));
    }

    @Test
    void prodProfile_fails() {
        List<String> violations = new ArrayList<>();
        SafetyGuardEnvironmentPostProcessor.validateProfiles(new String[]{"prod"}, violations);
        assertTrue(violations.stream().anyMatch(v -> v.contains("prod")));
    }

    @Test
    void stagingProfile_fails() {
        List<String> violations = new ArrayList<>();
        SafetyGuardEnvironmentPostProcessor.validateProfiles(new String[]{"staging"}, violations);
        assertTrue(violations.stream().anyMatch(v -> v.contains("staging")));
    }

    @Test
    void unknownProfile_fails() {
        List<String> violations = new ArrayList<>();
        SafetyGuardEnvironmentPostProcessor.validateProfiles(new String[]{"some-unknown-profile"}, violations);
        assertTrue(violations.stream().anyMatch(v -> v.contains("some-unknown-profile")));
    }

    @Test
    void noActiveProfile_fails() {
        List<String> violations = new ArrayList<>();
        SafetyGuardEnvironmentPostProcessor.validateProfiles(new String[]{}, violations);
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("default profile"));
    }

    @Test
    void mixOfAllowedAndDisallowedProfiles_fails() {
        List<String> violations = new ArrayList<>();
        SafetyGuardEnvironmentPostProcessor.validateProfiles(new String[]{"local", "production"}, violations);
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("production"));
    }

    // ------------------------------------------------------------------
    // Stage 5B - Snapshot Validation Gate (resolveEffectiveLegacyDbNames)
    // docs/real-data-audit/gops-stage5b-production-like-snapshot-environment.md §5/§6/§8
    // ------------------------------------------------------------------

    private static final String SNAPSHOT_DB_NAME = "goo_prod_snapshot_20260916";

    @Test
    void snapshotDbName_withoutSnapshotProfile_isDenied() throws URISyntaxException {
        // Case 2: snapshot DB name attempted, but snapshot-validation profile not active.
        Set<String> effective = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"test"}, SNAPSHOT_DB_NAME);
        assertEquals(SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES, effective);
        assertHasViolation("Legacy", "jdbc:mysql://localhost:33199/" + SNAPSHOT_DB_NAME, effective, "database name");
    }

    @Test
    void snapshotProfile_withoutExactDbProperty_isDenied() throws URISyntaxException {
        // Case 3: profile active, property absent (null) - grants nothing.
        Set<String> effective = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"snapshot-validation"}, null);
        assertEquals(SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES, effective);
        assertHasViolation("Legacy", "jdbc:mysql://localhost:33199/" + SNAPSHOT_DB_NAME, effective, "database name");
    }

    @Test
    void snapshotProfile_withBlankDbProperty_isDenied() {
        // Blank (not just null) must also grant nothing.
        Set<String> effective = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"snapshot-validation"}, "   ");
        assertEquals(SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES, effective);
    }

    @Test
    void exactDbProperty_withoutSnapshotProfile_isDenied() {
        // Case 4: property set, but snapshot-validation profile not active (e.g. plain "local").
        Set<String> effective = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"local"}, SNAPSHOT_DB_NAME);
        assertEquals(SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES, effective);
    }

    @Test
    void snapshotProfilePlusExactDbProperty_isAllowed() throws URISyntaxException {
        // Case 5: both present together - the only combination that grants the exception.
        Set<String> effective = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"local", "snapshot-validation"}, SNAPSHOT_DB_NAME);
        assertTrue(effective.contains(SNAPSHOT_DB_NAME));
        assertTrue(effective.contains("legacy_demo"), "Base allowlist must still be present, not replaced");
        assertNoViolations("Legacy", "jdbc:mysql://localhost:33199/" + SNAPSHOT_DB_NAME, effective);
    }

    @Test
    void wrongSnapshotDbName_isDenied() throws URISyntaxException {
        // Case 6: gate engaged for one exact name, but the actual URL uses a different name.
        Set<String> effective = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"snapshot-validation"}, SNAPSHOT_DB_NAME);
        assertHasViolation("Legacy", "jdbc:mysql://localhost:33199/some_other_snapshot_db", effective, "database name");
    }

    @Test
    void wildcardLikeConfiguredValue_isDenied() throws URISyntaxException {
        // Case 7: the configured property itself contains wildcard-looking characters -
        // must be treated as a literal string, never a pattern. "*" and "%" do not
        // magically match the real name.
        Set<String> effective = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"snapshot-validation"}, "goo_prod_snapshot_*");
        assertHasViolation("Legacy", "jdbc:mysql://localhost:33199/" + SNAPSHOT_DB_NAME, effective, "database name");

        Set<String> effectivePercent = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"snapshot-validation"}, "%");
        assertHasViolation("Legacy", "jdbc:mysql://localhost:33199/" + SNAPSHOT_DB_NAME, effectivePercent, "database name");
    }

    @Test
    void goo_isNeverAllowed_evenWithSnapshotProfileAndPropertySetToGoo() throws URISyntaxException {
        // Case 8a: the hard-deny check inside resolveEffectiveLegacyDbNames itself -
        // "goo" as the configured value must never be added to the effective set.
        Set<String> effective = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"snapshot-validation"}, "goo");
        assertEquals(SafetyGuardEnvironmentPostProcessor.ALLOWED_LEGACY_DB_NAMES, effective,
                "'goo' must never be added to the effective allowlist, even via explicit opt-in");
        assertHasViolation("Legacy", "jdbc:mysql://localhost:3306/goo", effective, "database name");
    }

    @Test
    void goo_isNeverAllowed_evenIfSomehowPresentInAllowedSet() throws URISyntaxException {
        // Case 8b: defense in depth - validateDataSource's own independent hard-deny
        // check must reject "goo" even if some future caller passed an allowedDbNames
        // set that (incorrectly) contains it.
        Set<String> allowedWithGooMistakenlyIncluded = Set.of("legacy_demo", "goo");
        assertHasViolation("Legacy", "jdbc:mysql://localhost:3306/goo",
                allowedWithGooMistakenlyIncluded, "permanently denied");
    }

    @Test
    void caseMismatchInConfiguredDbName_isDenied() throws URISyntaxException {
        // Case 9: exact match is case-SENSITIVE String.equals, not case-insensitive -
        // an uppercase-configured value must not match the real, lowercase URL name.
        Set<String> effective = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"snapshot-validation"}, SNAPSHOT_DB_NAME.toUpperCase());
        assertHasViolation("Legacy", "jdbc:mysql://localhost:33199/" + SNAPSHOT_DB_NAME, effective, "database name");
    }

    @Test
    void legacyDemo_stillAllowed_whenSnapshotProfileAlsoActive() throws URISyntaxException {
        // The base allowlist is never replaced, only ever additively expanded -
        // normal legacy_demo access must keep working unchanged even with the
        // snapshot-validation profile and property both present.
        Set<String> effective = SafetyGuardEnvironmentPostProcessor.resolveEffectiveLegacyDbNames(
                new String[]{"local", "snapshot-validation"}, SNAPSHOT_DB_NAME);
        assertNoViolations("Legacy", "jdbc:mysql://localhost:33061/legacy_demo", effective);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void assertNoViolations(String label, String jdbcUrl, Set<String> allowedDbNames) {
        List<String> violations = new ArrayList<>();
        SafetyGuardEnvironmentPostProcessor.validateDataSource(label, jdbcUrl, allowedDbNames, violations);
        assertTrue(violations.isEmpty(), "Expected no violations for " + jdbcUrl + " but got: " + violations);
    }

    private static void assertHasViolation(String label, String jdbcUrl, Set<String> allowedDbNames, String expectedSubstring) {
        List<String> violations = new ArrayList<>();
        SafetyGuardEnvironmentPostProcessor.validateDataSource(label, jdbcUrl, allowedDbNames, violations);
        assertTrue(violations.stream().anyMatch(v -> v.contains(expectedSubstring)),
                "Expected a violation containing '" + expectedSubstring + "' for " + jdbcUrl + " but got: " + violations);
    }
}
