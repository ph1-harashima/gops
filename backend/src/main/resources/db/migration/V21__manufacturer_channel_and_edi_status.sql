-- Prototype PostgreSQL only. Phase 9-D: Email / EDI branching
-- (Production-Oriented PO Workflow §13 Phase 4). Forward-only. Legacy MySQL
-- is never touched by any migration.
--
-- 1) manufacturer_channel: a new Portal-only Master (Legacy has no
--    Supplier communication-method Master at all, same situation V10's
--    supplier_contact/mail_template already documented) recording which
--    Channel (EMAIL/EDI) a Supplier[+Brand] actually uses - the ~90%/10%
--    split from the user's Working Assumption. Same shape/Uniqueness
--    policy as supplier_contact (V10): (supplier_code, COALESCE(brand_code,''))
--    unique among ACTIVE rows only, brand-specific row wins over
--    supplier-only at Resolution time. NOT added to DemoResetRunner's
--    TRUNCATE list - configuration-like Master data, same category as
--    supplier_contact/mail_template/portal_user.
--
-- 2) portal_order gains edi_status/edi_completed_by/edi_completed_at -
--    business-state tracking for the EDI path only (design doc's "EDI入力
--    待ち"/"EDI入力完了"), deliberately NOT modeled as a Workflow Status
--    (portal_order.status) - same "separate axis" principle already used
--    for communication_channel (V15) and Official PO Integration status
--    (V9). Null unless communication_channel = 'EDI'.
--
-- 3) New AuditEvent type: EDI_INPUT_COMPLETED.

CREATE TABLE manufacturer_channel (
    id                  BIGSERIAL PRIMARY KEY,
    supplier_code       VARCHAR(10)     NOT NULL,
    brand_code          VARCHAR(10),
    channel             VARCHAR(10)     NOT NULL,
    is_active           BOOLEAN         NOT NULL DEFAULT true,
    created_by          VARCHAR(50)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50)     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_manufacturer_channel_channel CHECK (channel IN ('EMAIL', 'EDI'))
);

CREATE UNIQUE INDEX uq_manufacturer_channel_active_identity ON manufacturer_channel
    (supplier_code, COALESCE(brand_code, ''))
    WHERE is_active = true;

ALTER TABLE portal_order ADD COLUMN edi_status VARCHAR(20);
ALTER TABLE portal_order ADD COLUMN edi_completed_by VARCHAR(50);
ALTER TABLE portal_order ADD COLUMN edi_completed_at TIMESTAMPTZ;

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
    'EDI_INPUT_COMPLETED'
));
