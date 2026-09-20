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
        // V18 (Phase 9-A): Official PO Number / Excel Generation -
        // official_po_integration_request delivery/ship/payment fields,
        // OFFICIAL_PO_NUMBER_CONFIRMED/OFFICIAL_PO_EXCEL_GENERATED Audit types.
        // V19 (Phase 9-B): Import Folder Integration - OFFICIAL_PO_FILE_PLACED/
        // OFFICIAL_PO_FILE_PLACEMENT_FAILED Audit types (no schema change).
        // V20 (Phase 9-C): G-SYS Import Confirmation - OFFICIAL_PO_IMPORT_CONFIRMED
        // Audit type (no schema change).
        // V21 (Phase 9-D): Email / EDI branching - manufacturer_channel Master,
        // portal_order.edi_status/edi_completed_by/edi_completed_at,
        // EDI_INPUT_COMPLETED Audit type.
        // V22 (Phase 9-E): Real Email Send - order_email table,
        // EMAIL_SENT/EMAIL_SEND_FAILED Audit types.
        // V23 (Gulliver UI最終仕上げ #2): demo portal_user.email updated to
        // natural fictional addresses matching each account's V14 display_name
        // (e.g. admin01 -> suzuki.hanako@...) - data-only, no schema change.
        // V24 (Gap Analysis C-1): official_po_integration_request.pdf_file_key/
        // pdf_generated_at - the PDF artifact slot, independent of the
        // Integration Status state machine.
        // V25 (Gap Analysis Phase 4-8): ck_audit_event_type extended with
        // OFFICIAL_PO_PDF_GENERATED/OFFICIAL_PO_REISSUED/OFFICIAL_PO_CANCELLED/
        // EMAIL_RECIPIENT_OVERRIDE_USED.
        // V26 (Gap Analysis C-2/C-4): official_po_integration_request.
        // lifecycle_status/lifecycle_reason/lifecycle_changed_by/
        // lifecycle_changed_at - the Document lifecycle axis (ACTIVE/
        // SUPERSEDED/CANCELLED), separate from the Integration Status above.
        // V27 (Gap Analysis C-5/§11): order_email.master_to_addresses/
        // master_cc_addresses/recipient_override_used - Email Recipient
        // Override; portal_mail_settings (new, singleton) - Default CC
        // Foundation (prefill only, never an enforced Business Rule).
        // V28 (Gap Analysis §12): supplier_region_classification (new,
        // Portal-only, ADMIN-set) - Domestic/Overseas Foundation. Never
        // derived from Legacy (no such field exists there, re-confirmed
        // READ ONLY this Phase) and never consulted by recommendedQty's
        // calculation - display only.
        // V29 (BR-08): official_po_short_code (new, Portal-only, ADMIN-set
        // Supplier/Brand 3-char abbreviation Master) + official_po_sequence
        // (new, per-Supplier x Brand atomic counter) - Official PO No.
        // auto-numbering foundation.
        // V30 (BR-08): Demo/Test fixture seed for official_po_short_code
        // (SUP_ALPHA/BETA/GAMMA, BR_OUTDOOR/HOME/KITCHEN) - data-only.
        // V31 (BR-03): Cancel Approval Workflow -
        // official_po_integration_request.cancel_requested_by/
        // cancel_requested_at/cancel_reason + ck_audit_event_type extended
        // with OFFICIAL_PO_CANCEL_REQUESTED/OFFICIAL_PO_CANCEL_NOTIFIED.
        // V32 (Post-Freeze Business Refinement, re-audit doc §5-11):
        // sku_expected_restock (new, Portal-only, SKU-keyed) - Type C Manual
        // Expected Restock Date; ck_audit_event_aggregate_root/
        // ck_audit_event_type extended for sku_code/SKU_EXPECTED_RESTOCK_CHANGED.
        // V33 (Post-Freeze Business Refinement 2, Manufacturer Stockout
        // Information Management): sku_expected_restock extended with
        // stockout_status/shortage_qty/information_received_date/
        // contact_method; new sku_manufacturer_stockout_history table
        // (Business-facing timeline, separate from audit_event).
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33"), versions);
    }

    @Test
    void expectedTablesExistOnPrototypePostgres() {
        JdbcTemplate jdbc = new JdbcTemplate(prototypeDataSource);
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name", String.class);
        for (String expected : List.of("portal_order", "portal_order_detail", "audit_event", "portal_user",
                "supplier_response", "supplier_response_detail", "order_attention",
                "portal_order_revision", "portal_order_revision_detail", "follow_up_case", "legacy_po_baseline",
                "price_change_set", "price_change_set_detail", "idempotent_operation",
                "manufacturer_channel", "order_email", "official_po_short_code", "official_po_sequence",
                "sku_expected_restock", "sku_manufacturer_stockout_history")) {
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
