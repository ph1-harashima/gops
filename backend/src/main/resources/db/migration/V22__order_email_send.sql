-- Prototype PostgreSQL only. Phase 9-E: Real Email Send
-- (Production-Oriented PO Workflow §13 Phase 5). Forward-only. Legacy MySQL
-- is never touched by any migration.
--
-- order_email: one row per (portal_order_id, revision_no) - the same
-- Idempotency-key shape as official_po_integration_request (V9). to_addresses/
-- cc_addresses are plain comma-separated TEXT (audit/display only, never
-- re-parsed for business logic) - matches this codebase's existing
-- preference for free-text columns over introducing a new array/JSONB type
-- for one denormalized display list (AuditEvent.note's own precedent).
--
-- attachment_type defaults to 'OFFICIAL_PO_EXCEL' but is a plain VARCHAR,
-- not a CHECK-constrained enum - deliberately not fixed to Excel-only
-- (Working Assumption §5: "Attachment形式を固定しすぎない設計"), so a future
-- OFFICIAL_PO_PDF (or similar) needs no schema change here.
--
-- Added to DemoResetRunner's TRUNCATE list (per-Order business-workflow
-- data, unlike manufacturer_channel/supplier_contact/mail_template which
-- are Master data DemoResetRunner deliberately preserves).

CREATE TABLE order_email (
    id                  BIGSERIAL PRIMARY KEY,
    portal_order_id     BIGINT          NOT NULL REFERENCES portal_order (id),
    revision_no         INTEGER         NOT NULL DEFAULT 1,
    from_address        VARCHAR(200),
    to_addresses        TEXT,
    cc_addresses        TEXT,
    subject             TEXT,
    body                TEXT,
    attachment_type     VARCHAR(50)     NOT NULL DEFAULT 'OFFICIAL_PO_EXCEL',
    attachment_file_key VARCHAR(255),
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    sent_at             TIMESTAMPTZ,
    sent_by             VARCHAR(50),
    error_code          VARCHAR(50),
    error_message       TEXT,
    retry_count         INTEGER         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_email_order_revision UNIQUE (portal_order_id, revision_no),
    CONSTRAINT ck_order_email_status CHECK (status IN ('DRAFT', 'SENT', 'FAILED'))
);

CREATE INDEX idx_order_email_portal_order_id ON order_email (portal_order_id);

ALTER TABLE audit_event DROP CONSTRAINT ck_audit_event_type;
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_type CHECK (event_type IN (
    'ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED',
    'SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED',
    'ATTENTION_ADDED','ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED',
    'ORDER_DATE_CHANGED','ORDER_RETURNED_TO_DRAFT',
    'SUBMITTED_FOR_APPROVAL','ORDER_APPROVED','APPROVED_WITH_CHANGES','RETURNED_FOR_CORRECTION',
    'OFFICIAL_PO_INTEGRATION_REQUESTED','PRECHECK_COMPLETED',
    'MAIL_PREVIEW_GENERATED',
    'SUPPLIER_RESPONSE_AGREED','ORDER_REVISION_CREATED','AGREEMENT_REOPENED',
    'FOLLOW_UP_CREATED','FOLLOW_UP_UPDATED','FOLLOW_UP_CLOSED','REORDER_CREATED',
    'LEGACY_PO_BASELINE_CAPTURED','LEGACY_PO_CHANGE_DETECTED',
    'EDI_SEND_RECORDED',
    'PRICE_CHANGE_SET_CREATED','PRICE_CHANGE_DETAIL_ADDED','PRICE_CHANGE_PROPOSED_PRICE_CHANGED',
    'PRICE_CHANGE_DETAIL_REMOVED','PRICE_CHANGE_NOTE_CHANGED',
    'OFFICIAL_PO_NUMBER_CONFIRMED','OFFICIAL_PO_EXCEL_GENERATED',
    'OFFICIAL_PO_FILE_PLACED','OFFICIAL_PO_FILE_PLACEMENT_FAILED',
    'OFFICIAL_PO_IMPORT_CONFIRMED',
    'EDI_INPUT_COMPLETED',
    'EMAIL_SENT','EMAIL_SEND_FAILED'
));
