-- Prototype PostgreSQL only. Phase 8-B: Price Change Foundation. Never
-- applied to Legacy (Flyway is bound exclusively to prototypeDataSource).
--
-- Design (docs/target-price-change-workflow.md 6章/16章): a Price Change
-- Request/Change Set is a NEW aggregate root, independent of portal_order -
-- confirmed in Phase 8-A that Price Change has NO Business/Technical
-- Dependency on Ordering (customer-review-decision-package.md 16.2章 row 7).
-- The State Model is deliberately the MINIMAL skeleton from Target Design
-- 6章 only: DRAFT/SUBMITTED/APPLIED/FAILED/CANCELLED. PENDING_APPROVAL/
-- APPROVED/SCHEDULED are NOT included - Phase 8-B instructions Section 1
-- explicitly forbids fixing Approval/Scheduled State ahead of CUSTOMER
-- REVIEW (target-price-change-workflow.md 17章 PC-6/PC-2/PC-3).

CREATE TABLE price_change_set (
    id              BIGSERIAL PRIMARY KEY,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPLIED', 'FAILED', 'CANCELLED')),
    note            TEXT,
    created_by      VARCHAR(50)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50)  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_price_change_set_status ON price_change_set (status);
CREATE INDEX idx_price_change_set_created_at ON price_change_set (created_at);

-- Line-level Detail. Baseline Snapshot fields (target-price-change-workflow.md
-- 7章/13章) are captured ONCE, at the moment a SKU is added to the Change Set
-- (via LegacyPriceReadRepository, a Legacy READ ONLY re-fetch - never trusted
-- from the Frontend, same convention as portal_order_detail's own Snapshot
-- fields). They are the Concurrency Check's comparison basis, NOT a Price
-- History record (History is a separate, later CUSTOMER REVIEW item - PC-5) -
-- this table itself is mutable (a DRAFT Detail row can still be edited/removed
-- while status='DRAFT'), unlike audit_event/portal_order_revision's
-- append-only convention.
CREATE TABLE price_change_set_detail (
    id                              BIGSERIAL PRIMARY KEY,
    price_change_set_id             BIGINT        NOT NULL REFERENCES price_change_set (id),
    item_cd                         VARCHAR(30)   NOT NULL,
    item_name_snapshot              VARCHAR(200),
    brand_code_snapshot             VARCHAR(10),
    item_grp_cd_snapshot            VARCHAR(30),
    baseline_prc_sell_w_tax         DECIMAL(10,2),
    baseline_cost_this_month_avg    DECIMAL(10,2),
    baseline_free_ship_flg          BOOLEAN,
    baseline_ship_fee               DECIMAL(10,2),
    baseline_captured_at            TIMESTAMPTZ   NOT NULL,
    proposed_prc_sell_w_tax         DECIMAL(10,2),
    created_at                      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_price_change_set_detail_sku UNIQUE (price_change_set_id, item_cd)
);

CREATE INDEX idx_price_change_set_detail_set_id ON price_change_set_detail (price_change_set_id);

-- audit_event generalization: Price Change is a second aggregate root that
-- needs the same append-only Audit Trail convention as portal_order, without
-- forcing a fabricated portal_order_id. portal_order_id is loosened to
-- nullable and a sibling price_change_set_id is added; exactly one of the
-- two must be set (never both, never neither) - this is the "common
-- infrastructure, technically appropriate sharing" case called out in
-- customer-review-decision-package.md 16.3章, as opposed to a duplicate
-- price_change_audit_event table that would just be a copy-pasted append-only
-- log with no other reason to differ.
ALTER TABLE audit_event ALTER COLUMN portal_order_id DROP NOT NULL;
ALTER TABLE audit_event ADD COLUMN price_change_set_id BIGINT REFERENCES price_change_set (id);
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_aggregate_root CHECK (
    (portal_order_id IS NOT NULL AND price_change_set_id IS NULL)
    OR (portal_order_id IS NULL AND price_change_set_id IS NOT NULL)
);

CREATE INDEX idx_audit_event_price_change_set_id_performed_at ON audit_event (price_change_set_id, performed_at);

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
    'PRICE_CHANGE_DETAIL_REMOVED','PRICE_CHANGE_NOTE_CHANGED'
));
