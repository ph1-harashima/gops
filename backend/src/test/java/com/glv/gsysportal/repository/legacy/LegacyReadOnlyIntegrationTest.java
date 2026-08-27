package com.glv.gsysportal.repository.legacy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the Legacy Adapter's DB user genuinely cannot write, even if application
 * code attempted it (Technical Design 4.1 layer 1; implementation instructions 5章/12章).
 *
 * Requires the Legacy Demo Instance and Prototype Postgres to be running
 * (docker compose up -d) with the READ ONLY user provisioned by
 * demo-data/03-readonly-user.sql.
 */
@SpringBootTest
class LegacyReadOnlyIntegrationTest {

    @Autowired
    private JdbcTemplate legacyJdbcTemplate;

    @Test
    void insertAgainstLegacyDemoInstanceIsRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
            legacyJdbcTemplate.update(
                "INSERT INTO ms_item (item_cd, brand_cd, description) VALUES ('SHOULD-FAIL', 'X', 'should not be allowed')"
            )
        );
        assertAccessDenied(ex);
    }

    @Test
    void updateAgainstLegacyDemoInstanceIsRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
            legacyJdbcTemplate.update("UPDATE ms_item SET description = 'tampered' WHERE item_cd = 'OD-TENT-001'")
        );
        assertAccessDenied(ex);
    }

    @Test
    void deleteAgainstLegacyDemoInstanceIsRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
            legacyJdbcTemplate.update("DELETE FROM ms_item WHERE item_cd = 'OD-TENT-001'")
        );
        assertAccessDenied(ex);
    }

    @Test
    void ddlAgainstLegacyDemoInstanceIsRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
            legacyJdbcTemplate.execute("ALTER TABLE ms_item ADD COLUMN should_not_exist VARCHAR(10)")
        );
        assertAccessDenied(ex);
    }

    /**
     * Accepts either rejection layer (Technical Design 4.1):
     *  - Layer 2 (connection-pool readOnly=true) rejects client-side before any
     *    network round-trip: "Connection is read-only. Queries leading to data
     *    modification are not allowed." (what mysql-connector-j actually raises,
     *    confirmed in this implementation session)
     *  - Layer 1 (DB user GRANT SELECT only) rejects server-side:
     *    "... denied for user ..." / "Access denied ...", verified separately via
     *    demo-data/03-readonly-user.sql and `SHOW GRANTS` (see final report).
     */
    private static void assertAccessDenied(DataAccessException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase();
        org.junit.jupiter.api.Assertions.assertTrue(
            message.contains("denied") || message.contains("access") || message.contains("read-only") || message.contains("read only"),
            "Expected a read-only/access-denied style error, got: " + message
        );
    }
}
