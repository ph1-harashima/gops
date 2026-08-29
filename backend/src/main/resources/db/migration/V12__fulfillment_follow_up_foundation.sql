-- Prototype PostgreSQL only. Phase 7-C7A: Fulfillment / Follow-up Foundation.
-- Never applied to Legacy (Flyway is bound exclusively to prototypeDataSource -
-- see PrototypeFlywayConfig and the Safety Gate). Legacy TR_PO/TR_PO_DTL/
-- TR_INV/TR_INV_DTL/TR_ARR/MS_STK are READ ONLY throughout this Phase - no
-- migration here creates, mirrors, or caches Legacy Fulfillment data; it is
-- always read live via FulfillmentReadRepository (docs/fulfillment-follow-up-foundation.md
-- 0章/2章 for the Source-derived formula).

-- 1) Follow-up Case (7-C7A 9章). A new Portal-only concept - no equivalent
--    exists anywhere in Legacy (0章's audit confirmed no Back Order/督促/
--    再発注 functionality exists in phasep-gulliver).
CREATE TABLE follow_up_case (
    id                  BIGSERIAL PRIMARY KEY,
    portal_order_id     BIGINT      NOT NULL REFERENCES portal_order(id),
    order_revision_id   BIGINT      REFERENCES portal_order_revision(id),
    -- Snapshotted at creation time (7-C4-Gate-independent) purely for display
    -- convenience/history - never re-read from portal_order at render time,
    -- so a Case still shows which PO No. it was actually raised against even
    -- if the Order's own officialPoNo field were ever to change later.
    official_po_no      VARCHAR(30),
    -- NULL = Order-level Case (not about one specific line).
    sku_code            VARCHAR(30),
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN', 'INQUIRY_PREPARED', 'CLOSED')),
    reason              VARCHAR(30) NOT NULL
                        CHECK (reason IN ('DELIVERY_OVERDUE', 'PARTIAL_DELIVERY', 'NO_ARRIVAL', 'QUANTITY_DIFFERENCE', 'OTHER')),
    note                TEXT,
    created_by          VARCHAR(50) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_by           VARCHAR(50),
    closed_at           TIMESTAMPTZ
);

CREATE INDEX idx_follow_up_case_order_id ON follow_up_case (portal_order_id);
CREATE INDEX idx_follow_up_case_status ON follow_up_case (status);

-- 2) Reorder Foundation (7-C7A 15章/16章): a Reorder Draft is just an
--    ordinary new portal_order row (created via the EXISTING
--    OrderDraftService.createDraft - zero change to that path, 28章's STOP
--    condition on "既存Order Workflowの大幅変更" does not apply) that
--    additionally carries a Reference back to what it was reordering from.
--    All three columns are nullable - every pre-existing/ordinary Order
--    simply leaves them NULL.
ALTER TABLE portal_order ADD COLUMN source_order_id BIGINT REFERENCES portal_order(id);
ALTER TABLE portal_order ADD COLUMN source_follow_up_case_id BIGINT REFERENCES follow_up_case(id);
ALTER TABLE portal_order ADD COLUMN reorder_reason TEXT;

-- 3) New Audit event types (7-C7A 21章). Legacy Fulfillment READ itself is
--    deliberately NOT audited every call (21章's explicit permission - it is
--    a pure informational view, matching every other plain GET endpoint in
--    this codebase, none of which are audited either).
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
    'FOLLOW_UP_CREATED','FOLLOW_UP_UPDATED','FOLLOW_UP_CLOSED','REORDER_CREATED'
));
