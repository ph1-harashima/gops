package com.glv.gsysportal.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for {@link DemoResetRunner#validateJdbcUrl}, the runtime
 * re-validation Demo Reset performs before issuing any TRUNCATE
 * (implementation instructions Step 5 5章). Deliberately does not exercise
 * {@link DemoResetRunner#run} with {@code enabled=true} in-process, since
 * that path calls {@link System#exit} on success - see
 * {@code backend/demo-reset.sh} and the manual verification recorded in
 * the Step 5 final report for the full end-to-end proof instead.
 */
class DemoResetRunnerTest {

    @Test
    void allowsLocalhostGsysPortal() {
        assertDoesNotThrow(() -> DemoResetRunner.validateJdbcUrl("jdbc:postgresql://localhost:54321/gsys_portal"));
    }

    @Test
    void allowsDockerServiceNameGsysPortal() {
        assertDoesNotThrow(() -> DemoResetRunner.validateJdbcUrl("jdbc:postgresql://prototype-postgres:5432/gsys_portal"));
    }

    @Test
    void rejectsExternalHost() {
        assertThrows(IllegalStateException.class,
                () -> DemoResetRunner.validateJdbcUrl("jdbc:postgresql://production.example.com:5432/gsys_portal"));
    }

    @Test
    void rejectsWrongDatabaseName() {
        assertThrows(IllegalStateException.class,
                () -> DemoResetRunner.validateJdbcUrl("jdbc:postgresql://localhost:54321/some_other_db"));
    }

    @Test
    void rejectsLegacySchemaNameEvenOnLocalhost() {
        assertThrows(IllegalStateException.class,
                () -> DemoResetRunner.validateJdbcUrl("jdbc:postgresql://localhost:54321/goo"));
    }

    @Test
    void rejectsUnparsableUrl() {
        assertThrows(IllegalStateException.class, () -> DemoResetRunner.validateJdbcUrl("not a url at all ::"));
    }
}
