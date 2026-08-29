-- Prototype PostgreSQL only. Phase 7-C6: Excel / Legacy Concurrency Control
-- Foundation. Never applied to Legacy (Flyway is bound exclusively to
-- prototypeDataSource - see PrototypeFlywayConfig and the Safety Gate).
--
-- Design (docs/excel-legacy-concurrency-control.md 4章/16章 for the full
-- reasoning): "the point-in-time G-SYS PO Portal actually confirmed" is
-- captured as an append-only Baseline row, separate from
-- official_po_integration_request (which tracks the Integration Workflow
-- axis, not a data Snapshot) - a dedicated Snapshot Entity was chosen over
-- adding columns to official_po_integration_request per 7-C6 4章's explicit
-- "将来監査を考えるとSnapshot Entityを別にする案を優先評価" guidance, mainly
-- because a Baseline may reasonably be re-captured multiple times for the
-- SAME (portal_order_id, revision_no) (e.g. an ADMIN re-confirms after
-- investigating a CHANGED result) and every capture should remain visible in
-- history, exactly like portal_order_revision/audit_event's own append-only
-- convention - never UPDATEd or DELETEd once created. The MOST RECENT row for
-- a given (portal_order_id, revision_no) is always the active Baseline used
-- by Compare (LegacyPoConcurrencyService).

CREATE TABLE legacy_po_baseline (
    id              BIGSERIAL PRIMARY KEY,
    portal_order_id BIGINT       NOT NULL REFERENCES portal_order (id),
    revision_no     INTEGER      NOT NULL,
    official_po_no  VARCHAR(30)  NOT NULL,
    fingerprint     VARCHAR(64)  NOT NULL,
    snapshot_json   TEXT         NOT NULL,
    captured_by     VARCHAR(50)  NOT NULL,
    captured_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_legacy_po_baseline_order_revision ON legacy_po_baseline (portal_order_id, revision_no);

-- Integration Intent (7-C6 12章/13章): distinguishes "this Official PO No. is
-- expected to be a brand-new G-SYS PO" (NEW) from "this Official PO No. is
-- expected to already exist in G-SYS and is being updated" (UPDATE) - so a
-- PO_NOT_FOUND Concurrency result is never automatically treated as an error
-- for a NEW-intent Order (7-C6 12章's explicit "勝手にPO_NOT_FOUNDをBLOCKED
-- にしない"). Nullable: the numbering rule itself is still unresolved
-- CUSTOMER REVIEW (7-C6 13章 "自動推定は慎重にする"), so this Phase never
-- infers a value - only an explicit ADMIN action ever sets it.
ALTER TABLE official_po_integration_request ADD COLUMN integration_intent VARCHAR(10)
    CHECK (integration_intent IS NULL OR integration_intent IN ('NEW', 'UPDATE'));

-- New Audit event types (7-C6 18章/21章): LEGACY_PO_CHANGE_DETECTED is
-- written ONLY when Compare finds CHANGED (never for UNCHANGED, to avoid
-- generating a large volume of "confirmed no change" Audit noise on every
-- Order Detail page view - 7-C6 18章's explicit guidance).
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
    'LEGACY_PO_BASELINE_CAPTURED','LEGACY_PO_CHANGE_DETECTED'
));
