-- Post-Freeze Business Refinement (docs/gops-20260917-business-requirements-re-audit.md
-- §5-11): Long-term OOS specifically means "no PO in flight" (DashboardService's
-- own isLongTermOutOfStock formula), so exactly the SKUs a user would most
-- want a restock date for are the ones Legacy has no ETA/ETA_WH for at all
-- (nothing's been ordered yet). This is therefore a NEW, Portal-only,
-- Manual Input record - never read from or written to Legacy, and
-- independent of Brand/Supplier/Official PO Short Code Data Model (explicit
-- instruction not to touch those this round).
--
-- One row per SKU (not per Supplier/Brand) - updated in place, never
-- soft-deleted like the other Master tables, since "no restock info entered
-- yet" is already fully expressed by the row simply not existing.
CREATE TABLE sku_expected_restock (
    id                      BIGSERIAL PRIMARY KEY,
    sku_code                VARCHAR(50)     NOT NULL,
    expected_restock_date   DATE,
    is_unknown              BOOLEAN         NOT NULL DEFAULT false,
    memo                    VARCHAR(500),
    created_by              VARCHAR(50)     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by              VARCHAR(50)     NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    -- "未定と確認済み" (affirmatively Unknown) and "a real date" are two
    -- distinct, mutually exclusive states (re-audit doc §6) - never both at
    -- once. A row with neither set simply means "not yet looked into",
    -- expressed by is_unknown=false + a null date (the column defaults).
    CONSTRAINT ck_sku_expected_restock_unknown_xor_date
        CHECK (NOT (is_unknown = true AND expected_restock_date IS NOT NULL))
);

CREATE UNIQUE INDEX uq_sku_expected_restock_sku_code ON sku_expected_restock (sku_code);

-- Re-use the existing Audit Trail infrastructure (audit_event) rather than
-- inventing a parallel mechanism - same "one nullable FK per aggregate root
-- + a CHECK enforcing exactly one is set" pattern price_change_set_id
-- already established for Price Change (V16). sku_code is not a Portal-DB
-- foreign key (no local sku table - SKU is a Legacy-sourced code), so this
-- is a plain nullable VARCHAR column, not a FK.
ALTER TABLE audit_event ADD COLUMN sku_code VARCHAR(50);

ALTER TABLE audit_event DROP CONSTRAINT ck_audit_event_aggregate_root;
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_aggregate_root CHECK (
    (portal_order_id IS NOT NULL AND price_change_set_id IS NULL AND sku_code IS NULL) OR
    (portal_order_id IS NULL AND price_change_set_id IS NOT NULL AND sku_code IS NULL) OR
    (portal_order_id IS NULL AND price_change_set_id IS NULL AND sku_code IS NOT NULL)
);

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
    'OFFICIAL_PO_CANCEL_NOTIFIED','SKU_EXPECTED_RESTOCK_CHANGED'
));

CREATE INDEX idx_audit_event_sku_code_performed_at ON audit_event (sku_code, performed_at);
