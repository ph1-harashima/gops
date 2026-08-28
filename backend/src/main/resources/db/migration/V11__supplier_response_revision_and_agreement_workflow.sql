-- Prototype PostgreSQL only. Phase 7-C5: Supplier Response Revision /
-- Agreement Workflow. Never applied to Legacy (Flyway is bound exclusively
-- to prototypeDataSource - see PrototypeFlywayConfig and the Safety Gate).
--
-- Design decision (docs/supplier-response-revision-workflow.md 2章 for the
-- full reasoning): a Revision "crystallizes" - is written as an immutable
-- portal_order_revision/_detail snapshot - at the moment an Order is
-- (re-)sent to the Supplier (OrderStatusTransitionService.demoSend), exactly
-- mirroring how supplier_response_detail.ordered_qty has always been
-- snapshotted at that same instant. Between a "修正版を作成" action and the
-- next Send, the Order is simply back in ordinary STATUS_DRAFT and is edited
-- via the EXISTING Draft screen/API (OrderDraftController) - portal_order_detail
-- itself is NEVER restructured or duplicated per-Revision. This is why no
-- change to portal_order_detail's shape is needed (Phase 7-C5 0章/30章 STOP
-- trigger (a) - "Revision導入がOrder Detail構造の全面書き換えを要する" - does
-- not apply).

-- 1) Order Revision snapshot (Phase 7-C5 2章/4章). One row per Order per Send.
--    Never UPDATEd or DELETEd once created (append-only, like audit_event).
CREATE TABLE portal_order_revision (
    id              BIGSERIAL PRIMARY KEY,
    portal_order_id BIGINT      NOT NULL REFERENCES portal_order(id),
    revision_no     INTEGER     NOT NULL,
    revision_type   VARCHAR(20) NOT NULL CHECK (revision_type IN ('INITIAL', 'CORRECTION')),
    reason          TEXT,
    created_by      VARCHAR(50) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_portal_order_revision UNIQUE (portal_order_id, revision_no),
    -- INITIAL (Revision 1) never carries a reason (nothing to explain yet);
    -- CORRECTION (Revision 2+) always does (Phase 7-C5 14章 "変更理由を必須
    -- 推奨" - made a hard requirement here since it is cheap to enforce and
    -- the resulting reason is genuinely useful on the History screen).
    CONSTRAINT ck_portal_order_revision_reason CHECK (
        (revision_type = 'INITIAL' AND reason IS NULL)
        OR (revision_type = 'CORRECTION' AND reason IS NOT NULL AND length(trim(reason)) > 0)
    )
);

CREATE INDEX idx_portal_order_revision_order_id ON portal_order_revision (portal_order_id);

-- line_no/currency are intentionally NOT duplicated here: line_no is a pure
-- display-ordering concern (re-derived from portal_order_detail.line_no via
-- sku_code join for History rendering) and currency lives once per Order
-- (portal_order.currency) - Phase 7-C5 2章's candidate field list included
-- both, but this Phase deliberately follows the "snapshot exactly what can
-- change between Revisions" principle already used by supplier_response_detail
-- (Technical Design 5.4) rather than duplicating fields that cannot vary
-- per-line within a single Order.
CREATE TABLE portal_order_revision_detail (
    id                  BIGSERIAL PRIMARY KEY,
    revision_id         BIGINT      NOT NULL REFERENCES portal_order_revision(id) ON DELETE CASCADE,
    sku_code            VARCHAR(30) NOT NULL,
    item_name_snapshot  VARCHAR(200) NOT NULL,
    recommended_qty     INTEGER     NOT NULL,
    ordered_qty         INTEGER     NOT NULL,
    requested_delivery  DATE,
    unit_price          NUMERIC(15, 2),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_portal_order_revision_detail_revision_id ON portal_order_revision_detail (revision_id);

-- 2) portal_order gains a pointer to "the Revision most recently sent"
--    (Phase 7-C5 3章's "非常に重要": this is the SAME concept as
--    official_po_integration_request.revision_no, not a separate one).
--    NULL until the first Send, mirroring the existing prototype_po_no/
--    official_po_no "unassigned until milestone" idiom already used twice in
--    this table.
ALTER TABLE portal_order ADD COLUMN current_revision_no INTEGER;

-- Existing Orders that have already been sent at least once (i.e. already
-- have a supplier_response row) are migrated to Revision 1, reconstructed
-- from their CURRENT portal_order_detail/supplier_response_detail state -
-- safe because, before this migration, no Revision-2+ concept existed at
-- all, so "current state" and "what was actually sent" are still identical
-- for every existing row (Phase 7-C5 4章).
INSERT INTO portal_order_revision (portal_order_id, revision_no, revision_type, reason, created_by, created_at)
SELECT po.id, 1, 'INITIAL', NULL, po.created_by, po.created_at
FROM portal_order po
WHERE EXISTS (SELECT 1 FROM supplier_response sr WHERE sr.portal_order_id = po.id);

INSERT INTO portal_order_revision_detail
    (revision_id, sku_code, item_name_snapshot, recommended_qty, ordered_qty, requested_delivery, unit_price, created_at)
SELECT r.id, pod.sku, pod.item_name_snapshot, pod.recommended_qty, srd.ordered_qty, srd.requested_delivery, pod.unit_price, r.created_at
FROM portal_order_revision r
JOIN supplier_response sr ON sr.portal_order_id = r.portal_order_id
JOIN supplier_response_detail srd ON srd.supplier_response_id = sr.id
JOIN portal_order_detail pod ON pod.id = srd.portal_order_detail_id
WHERE r.revision_no = 1;

UPDATE portal_order po SET current_revision_no = 1
WHERE EXISTS (SELECT 1 FROM supplier_response sr WHERE sr.portal_order_id = po.id);

-- 3) Response-Revision link (Phase 7-C5 5章): relax the previous hard 1:1
--    Order<->Response constraint to 1:1 per (Order, Revision) instead, so a
--    Revision 2 Send can create its own new supplier_response row while the
--    Revision 1 response row stays fully intact, untouched, and readable as
--    history - no data is migrated or deleted, this is purely additive.
ALTER TABLE supplier_response ADD COLUMN order_revision_id BIGINT REFERENCES portal_order_revision(id);

UPDATE supplier_response sr SET order_revision_id = r.id
FROM portal_order_revision r
WHERE r.portal_order_id = sr.portal_order_id AND r.revision_no = 1;

ALTER TABLE supplier_response ALTER COLUMN order_revision_id SET NOT NULL;
ALTER TABLE supplier_response DROP CONSTRAINT supplier_response_portal_order_id_key;
ALTER TABLE supplier_response ADD CONSTRAINT uq_supplier_response_order_revision UNIQUE (portal_order_id, order_revision_id);

-- 3b) Supply Status (Phase 7-C5 7章): a per-line field the Supplier-response
--     answerer explicitly selects - NEVER auto-inferred from confirmed_qty
--     (e.g. confirmed_qty=0 must NOT automatically imply OUT_OF_STOCK). NULL
--     means "not yet selected", distinct from the explicit 'UNKNOWN' choice -
--     the same NULL-vs-explicit-value idiom as confirmed_qty (7-C5 6章/11章's
--     "0と null"). The official business definition of each value remains
--     [TBD - CUSTOMER REVIEW] (7-C5 24章); this Phase only stores the
--     ADMIN/OPERATOR-selected value verbatim.
ALTER TABLE supplier_response_detail ADD COLUMN supply_status VARCHAR(30)
    CHECK (supply_status IN ('AVAILABLE', 'OUT_OF_STOCK', 'LONG_TERM_OUT_OF_STOCK',
                              'DISCONTINUED', 'WAITING_FOR_ARRIVAL', 'UNKNOWN'));

-- 3c) Difference Detection's SUPPLY_STATUS_CHANGED Attention type (7-C5 8章/9章),
--     added to the existing Attention type allow-list alongside the two Step-4
--     types it already had.
ALTER TABLE order_attention DROP CONSTRAINT order_attention_attention_type_check;
ALTER TABLE order_attention ADD CONSTRAINT order_attention_attention_type_check CHECK (attention_type IN
    ('QUANTITY_CHANGED', 'DELIVERY_CHANGED', 'PARTIAL_CONFIRMATION',
     'DATA_OUTDATED', 'OTHER_ATTENTION', 'SUPPLY_STATUS_CHANGED'));

-- 4) Agreement (Phase 7-C5 11章) and Reopen (18章) fields, added directly to
--    supplier_response rather than a new table - Agreement is 1:1 with a
--    specific (CONFIRMED) Response, matching this codebase's established
--    preference for a few extra columns over a new join table (e.g. how
--    official_po_no was added as a plain column, not a side table).
--    agreed_at/agreed_by are never cleared on Reopen (Phase 7-C5 18章
--    "履歴を消さない" - direct data overwrite is forbidden); reopened_at/by/
--    reason record the most recent Reopen alongside them. The full N-cycle
--    agree/reopen history beyond "most recent of each" is always fully
--    reconstructable from the append-only audit_event trail
--    (SUPPLIER_RESPONSE_AGREED / AGREEMENT_REOPENED rows), same as every
--    other "header holds latest, audit_event holds full history" field in
--    this schema.
ALTER TABLE supplier_response ADD COLUMN agreed_by VARCHAR(50);
ALTER TABLE supplier_response ADD COLUMN agreed_at TIMESTAMPTZ;
ALTER TABLE supplier_response ADD COLUMN reopened_by VARCHAR(50);
ALTER TABLE supplier_response ADD COLUMN reopened_at TIMESTAMPTZ;
ALTER TABLE supplier_response ADD COLUMN reopen_reason TEXT;

-- 5) Workflow Status (Phase 7-C5 19章): audit confirmed the currently
--    reachable set is DRAFT/PENDING_APPROVAL/APPROVED/SENT/AWAITING_SUPPLIER/
--    SUPPLIER_CONFIRMED; COMPLETED has been in this CHECK constraint's
--    allow-list since V1 but has no Java constant and no code path ever
--    writes it (a pre-existing dead value) - left untouched rather than
--    removed, since dropping it is out of this Phase's scope and not
--    something any existing row depends on either way. AGREED is the only
--    genuinely new value this Phase adds (SUPPLIER_CONFIRMED -> AGREED via
--    the new Agreement Business Action; "Supplier Response確定 ≠ AGREED"
--    continues to hold, since AGREED requires a separate explicit ADMIN act).
ALTER TABLE portal_order DROP CONSTRAINT ck_portal_order_status;
ALTER TABLE portal_order ADD CONSTRAINT ck_portal_order_status CHECK (status IN
    ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SENT', 'AWAITING_SUPPLIER', 'SUPPLIER_CONFIRMED', 'COMPLETED', 'AGREED'));

-- 6) New Audit event types (Phase 7-C5 23章). Only 3 genuinely new types are
--    added - SUPPLIER_RESPONSE_SAVED and ORDER_REVISION_UPDATED were audited
--    for overlap and deliberately NOT added: Save Supplier Response already
--    fires per-field QUANTITY_CHANGED/DELIVERY_CHANGED rows (no separate
--    "a Save happened" marker existed before this Phase either, so adding one
--    now would be new noise, not filling a gap), and a Revision-N+1 edit
--    reuses the EXISTING Draft edit endpoint/audit rows (ORDER_QTY_CHANGED
--    etc.) verbatim - seeing those rows appear between an ORDER_REVISION_CREATED
--    and the next DEMO_SENT already fully identifies them as "edits during a
--    correction" without a redundant parallel event type.
ALTER TABLE audit_event DROP CONSTRAINT ck_audit_event_type;
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_type CHECK (event_type IN (
    'ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED',
    'SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED',
    'ATTENTION_ADDED','ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED',
    'ORDER_DATE_CHANGED','ORDER_RETURNED_TO_DRAFT',
    'SUBMITTED_FOR_APPROVAL','ORDER_APPROVED','APPROVED_WITH_CHANGES','RETURNED_FOR_CORRECTION',
    'OFFICIAL_PO_INTEGRATION_REQUESTED','PRECHECK_COMPLETED',
    'MAIL_PREVIEW_GENERATED',
    'SUPPLIER_RESPONSE_AGREED','ORDER_REVISION_CREATED','AGREEMENT_REOPENED'
));
