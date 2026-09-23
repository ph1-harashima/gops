-- G-OPS Operational Workflow Realignment, Phase A (docs/ux-audit/
-- gops-operational-workflow-realignment-implementation.md): domain audit
-- found 4 of the 5 required axes (Order status, G-SYS Integration status,
-- Lifecycle/Reissue-Cancel status, Supplier communication channel/EDI
-- status) already correctly separated. Document state is already implicit
-- in the existing nullable generated_file_key/pdf_file_key columns. The
-- one genuinely missing axis is Signature - the Critical Design Principle
-- that "Admin approved" must never be conflated with "signed PDF
-- confirmed" has no field to express it today. This migration adds that
-- axis only - no other column, no other table, no rewrite of any existing
-- column (V26's own "no DB-level CHECK on lifecycle_status" precedent is
-- followed here too - guarded in the Entity/Service layer only).
ALTER TABLE official_po_integration_request
    ADD COLUMN signature_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN signed_pdf_file_key VARCHAR(255),
    ADD COLUMN signed_at TIMESTAMPTZ,
    ADD COLUMN signed_by VARCHAR(50);

-- Same drop-and-recreate-the-whole-allowlist pattern every prior feature
-- migration touching this constraint has used (V32 is the immediately
-- preceding version - carried forward verbatim plus this migration's own
-- 2 new values only).
ALTER TABLE audit_event DROP CONSTRAINT ck_audit_event_type;
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_type CHECK (event_type IN (
    'ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED',
    'SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED','ATTENTION_ADDED',
    'ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED','ORDER_DATE_CHANGED',
    'ORDER_RETURNED_TO_DRAFT','SUBMITTED_FOR_APPROVAL','ORDER_APPROVED','APPROVED_WITH_CHANGES',
    'RETURNED_FOR_CORRECTION','OFFICIAL_PO_INTEGRATION_REQUESTED','PRECHECK_COMPLETED',
    'MAIL_PREVIEW_GENERATED','SUPPLIER_RESPONSE_AGREED','ORDER_REVISION_CREATED',
    'AGREEMENT_REOPENED','FOLLOW_UP_CREATED','FOLLOW_UP_UPDATED','FOLLOW_UP_CLOSED',
    'REORDER_CREATED','LEGACY_PO_BASELINE_CAPTURED','LEGACY_PO_CHANGE_DETECTED',
    'EDI_SEND_RECORDED','PRICE_CHANGE_SET_CREATED','PRICE_CHANGE_DETAIL_ADDED',
    'PRICE_CHANGE_PROPOSED_PRICE_CHANGED','PRICE_CHANGE_DETAIL_REMOVED','PRICE_CHANGE_NOTE_CHANGED',
    'OFFICIAL_PO_NUMBER_CONFIRMED','OFFICIAL_PO_EXCEL_GENERATED','OFFICIAL_PO_FILE_PLACED',
    'OFFICIAL_PO_FILE_PLACEMENT_FAILED','OFFICIAL_PO_IMPORT_CONFIRMED','EDI_INPUT_COMPLETED',
    'EMAIL_SENT','EMAIL_SEND_FAILED','OFFICIAL_PO_PDF_GENERATED','OFFICIAL_PO_REISSUED',
    'OFFICIAL_PO_CANCELLED','EMAIL_RECIPIENT_OVERRIDE_USED','OFFICIAL_PO_CANCEL_REQUESTED',
    'OFFICIAL_PO_CANCEL_NOTIFIED','SKU_EXPECTED_RESTOCK_CHANGED',
    'OFFICIAL_PO_SIGNATURE_PENDING','OFFICIAL_PO_SIGNED'
));
