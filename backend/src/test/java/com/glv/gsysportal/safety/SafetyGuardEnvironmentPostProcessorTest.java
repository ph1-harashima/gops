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
    @ValueSource(strings = {"local", "demo", "test"})
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
