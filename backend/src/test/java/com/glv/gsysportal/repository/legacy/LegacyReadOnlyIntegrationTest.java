package com.glv.gsysportal.repository.legacy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the Legacy Adapter's DB user genuinely cannot write, even if application
 * code attempted it (Technical Design 4.1 layer 1; implementation instructions 5章/12章).
 *
 * Requires the Legacy Demo Instance and Prototype Postgres to be running
 * (docker compose up -d) with the READ ONLY user provisioned by
 * demo-data/03-readonly-user.sql.
 *
 * "test" is on the Safety Gate's profile allowlist (Step 2 0.3) - the
 * application now refuses to start under the default (no-profile) case.
 */
@SpringBootTest
@ActiveProfiles("test")
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

    /** Phase 7-C2A: Official PO Integration's Preflight reads tr_po-adjacent
     * Master tables but must never be able to write TR_PO itself - the exact
     * table PrOfficialPoImportBatch writes (7-C2A 21章 Safety Guard regression). */
    @Test
    void insertAgainstTrPoIsRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
            legacyJdbcTemplate.update(
                "INSERT INTO tr_po (po_no, status, supplier_cd, brand_cd) VALUES ('SHOULD-FAIL', 'OFFICIAL', 'X', 'Y')"
            )
        );
        assertAccessDenied(ex);
    }

    /** Phase 7-C6: LegacyPoConcurrencyReadRepository reads TR_PO_DTL's
     * commercial columns (qty_po/prc_unit) but must never be able to write
     * them - the exact table a real Legacy Handoff (7-C2B, still not
     * implemented) would eventually need to stay READ ONLY against today. */
    @Test
    void updateAgainstTrPoDtlIsRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
            legacyJdbcTemplate.update("UPDATE tr_po_dtl SET qty_po = 999 WHERE po_no = 'PO-CONC-01'")
        );
        assertAccessDenied(ex);
    }

    /** Phase 8-B: LegacyPriceReadRepository reads ms_item.prc_sell_w_tax/
     * cost_this_month_avg/item_grp_cd (V16-equivalent Demo schema columns,
     * backend/demo-data/01-schema.sql) but must never be able to write them -
     * proves Price Change Foundation's Baseline capture path is exactly as
     * READ ONLY as every other Legacy Adapter. */
    @Test
    void updatePriceColumnsAgainstLegacyDemoInstanceIsRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
            legacyJdbcTemplate.update("UPDATE ms_item SET prc_sell_w_tax = 999999.00 WHERE item_cd = 'OD-TENT-001'")
        );
        assertAccessDenied(ex);
    }

    /** Phase 8-G: ArrivalReadRepository reads the new tr_arr table (backend/
     * demo-data/01-schema.sql) but must never be able to write it - proves
     * the READ ONLY guarantee automatically covers a table added after this
     * test class already existed (GRANT SELECT ON legacy_demo.* is
     * database-wide, demo-data/03-readonly-user.sql). */
    @Test
    void insertAgainstTrArrIsRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
            legacyJdbcTemplate.update(
                "INSERT INTO tr_arr (supplier_cd, po_no, inv_no, qty) VALUES ('SHOULD-FAIL', 'PO-SHOULD-FAIL', 'INV-SHOULD-FAIL', 1)"
            )
        );
        assertAccessDenied(ex);
    }

    /** Phase 8-G: WarehouseStockReadRepository reads ms_stk.stk_qty but must
     * never be able to write it - the exact column Warehouse Stock
     * Visibility displays. */
    @Test
    void updateMsStkQuantityAgainstLegacyDemoInstanceIsRejected() {
        DataAccessException ex = assertThrows(DataAccessException.class, () ->
            legacyJdbcTemplate.update("UPDATE ms_stk SET stk_qty = 999999 WHERE wh_cd = '04' AND item_cd = 'OD-TENT-001'")
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
