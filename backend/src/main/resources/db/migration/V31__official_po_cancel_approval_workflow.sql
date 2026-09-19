-- BR-03 (docs/gulliver-20260917-confirmed-business-rules.md): Cancel is now
-- a two-step Workflow (Cancel Request -> ADMIN Approval -> Notification ->
-- CANCELLED), not a single direct state change. lifecycle_status itself has
-- no DB-level CHECK constraint (V26's own note: enforced in the Service/
-- Entity layer only) - only the new columns capturing the Cancel Request's
-- own actor/reason (distinct from lifecycle_changed_by/lifecycle_changed_at,
-- which - once CANCELLED - reflect the ADMIN who APPROVED it) need adding.
ALTER TABLE official_po_integration_request
    ADD COLUMN cancel_requested_by VARCHAR(50),
    ADD COLUMN cancel_requested_at TIMESTAMPTZ,
    ADD COLUMN cancel_reason TEXT;

-- Same drop-and-recreate-the-whole-allowlist pattern every prior feature
-- migration touching this constraint has used (V25 is the immediately
-- preceding version).
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
    'EMAIL_SENT','EMAIL_SEND_FAILED',
    'OFFICIAL_PO_PDF_GENERATED','OFFICIAL_PO_REISSUED','OFFICIAL_PO_CANCELLED',
    'EMAIL_RECIPIENT_OVERRIDE_USED',
    'OFFICIAL_PO_CANCEL_REQUESTED','OFFICIAL_PO_CANCEL_NOTIFIED'
));
