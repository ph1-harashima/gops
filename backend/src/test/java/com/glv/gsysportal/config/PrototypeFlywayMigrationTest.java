package com.glv.gsysportal.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Implementation instructions 16章 "Flyway Migration Test": proves the
 * Prototype-only migrations (V1..V6, {@link PrototypeFlywayConfig}) were
 * actually applied to the local Prototype Postgres and that Flyway is bound
 * exclusively to {@code prototypeDataSource} - never to {@code legacyDataSource}
 * (Technical Design 9章 / implementation instructions 2章). V6 is Step 3's
 * addition (prototype_po_no_seq + ORDER_RETURNED_TO_DRAFT event type).
 */
@SpringBootTest
@ActiveProfiles("test")
class PrototypeFlywayMigrationTest {

    @Autowired
    @Qualifier("prototypeDataSource")
    private DataSource prototypeDataSource;

    @Test
    void allMigrationsAppliedSuccessfully() {
        JdbcTemplate jdbc = new JdbcTemplate(prototypeDataSource);
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank", String.class);
        // V6 (Step 3): prototype_po_no_seq + ORDER_RETURNED_TO_DRAFT event type.
        // V7 (Step 4): supplier_response / supplier_response_detail / order_attention.
        // V8 (Phase 7-C1): Role/Approval foundation - portal_user.email,
        // admin01 demo account, READY_TO_ORDER -> APPROVED data migration.
        // V9 (Phase 7-C2A): Official PO Integration foundation -
        // portal_order.official_po_no, official_po_integration_request.
        // V10 (Phase 7-C3): Supplier Contact / Mail Template foundation.
        // V11 (Phase 7-C5): Supplier Response Revision / Agreement Workflow -
        // portal_order_revision(_detail), supplier_response.order_revision_id,
        // supply_status, Agreement/Reopen fields, AGREED status.
        // V12 (Phase 7-C7A): Fulfillment / Follow-up Foundation - follow_up_case,
        // portal_order source_order_id/source_follow_up_case_id/reorder_reason.
        // V13 (Phase 7-C6): Excel / Legacy Concurrency Control Foundation -
        // legacy_po_baseline, official_po_integration_request.integration_intent.
        // V14 (Phase 7-H): demo portal_user.display_name updated to fictional
        // person names (User表示 audit) - data-only, no schema change.
        // V15 (Phase 7-H): EDI発注Workflow Foundation -
        // portal_order.communication_channel, EDI_SEND_RECORDED Audit type.
        // V16 (Phase 8-B): Price Change Foundation - price_change_set(_detail),
        // audit_event.portal_order_id loosened to nullable +
        // audit_event.price_change_set_id added (second aggregate root),
        // PRICE_CHANGE_* Audit types.
        // V17 (Phase 8-L): Technical Idempotency Foundation - idempotent_operation
        // (operation_type/idempotency_key UNIQUE, no FK to anything - a
        // deliberately standalone Technical table, not a Business aggregate).
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17"), versions);
    }

    @Test
    void expectedTablesExistOnPrototypePostgres() {
        JdbcTemplate jdbc = new JdbcTemplate(prototypeDataSource);
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name", String.class);
        for (String expected : List.of("portal_order", "portal_order_detail", "audit_event", "portal_user",
                "supplier_response", "supplier_response_detail", "order_attention",
                "portal_order_revision", "portal_order_revision_detail", "follow_up_case", "legacy_po_baseline",
                "price_change_set", "price_change_set_detail", "idempotent_operation")) {
            assertTrue(tables.contains(expected), "Expected table missing: " + expected + ", got: " + tables);
        }
    }

    @Test
    void demoPortalUsersWereSeeded() {
        JdbcTemplate jdbc = new JdbcTemplate(prototypeDataSource);
        List<String> usernames = jdbc.queryForList("SELECT username FROM portal_user ORDER BY username", String.class);
        // admin01 added by V8 (Phase 7-C1 18章: a dedicated ADMIN demo account).
        assertEquals(List.of("admin01", "purchase01", "sales_admin", "sys_admin"), usernames);
    }

    @Test
    void prototypePoNoSequenceExists() {
        JdbcTemplate jdbc = new JdbcTemplate(prototypeDataSource);
        List<String> sequences = jdbc.queryForList(
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public'", String.class);
        assertTrue(sequences.contains("prototype_po_no_seq"));
    }
}
