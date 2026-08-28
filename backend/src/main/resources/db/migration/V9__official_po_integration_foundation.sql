-- Prototype PostgreSQL only. Phase 7-C2A: Official PO Integration Foundation.
-- Forward-only. Legacy MySQL is never touched by any migration.
--
-- 1) portal_order.official_po_no: the G-SYS Official PO No. concept, kept
--    deliberately separate from prototype_po_no (Portal-internal display
--    number, PO-DEMO-yyyyMMdd-####) and draft_no (Portal Draft number) -
--    docs/official-po-integration-detailed-design.md 5.2章. Always NULL this
--    Phase: no code path assigns it (the real numbering rule is unresolved
--    CUSTOMER REVIEW, 7-C2A 7章's Gate).
--
-- 2) official_po_integration_request: one row per (portal_order_id,
--    revision_no) - the Idempotency key (7-C2A 4章). revision_no is always 1
--    this Phase (no Revision Workflow exists yet, 7-C2A 5章). status is a
--    axis entirely separate from portal_order.status (Business Workflow
--    Status) - 7-C2-Design 18章. This Phase only ever writes 'PENDING';
--    GENERATED/SUBMITTED/CONFIRMED/FAILED exist in the State Model for
--    7-C2B+ and are reachable only via direct entity-method Tests this Phase.
--
-- 3) New AuditEvent types: OFFICIAL_PO_INTEGRATION_REQUESTED,
--    PRECHECK_COMPLETED (7-C2A 19章). The full allow-list is repeated (V6's
--    pattern), not just the additions, since Postgres CHECK constraints are
--    replaced wholesale. event_type is also widened 30->50 here -
--    OFFICIAL_PO_INTEGRATION_REQUESTED is 33 characters, past the original
--    30-char column limit (every prior event_type value fit within 30, so
--    this was never hit before).

ALTER TABLE portal_order ADD COLUMN official_po_no VARCHAR(30);

CREATE UNIQUE INDEX uq_portal_order_official_po_no ON portal_order (official_po_no)
    WHERE official_po_no IS NOT NULL;

CREATE TABLE official_po_integration_request (
    id                      BIGSERIAL PRIMARY KEY,
    portal_order_id         BIGINT          NOT NULL REFERENCES portal_order (id),
    revision_no             INTEGER         NOT NULL DEFAULT 1,
    official_po_no          VARCHAR(30),
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    generated_file_key      VARCHAR(255),
    requested_by            VARCHAR(50)     NOT NULL,
    requested_at            TIMESTAMPTZ     NOT NULL,
    generated_at            TIMESTAMPTZ,
    submitted_at            TIMESTAMPTZ,
    confirmed_at            TIMESTAMPTZ,
    failed_at               TIMESTAMPTZ,
    error_code              VARCHAR(50),
    error_message           TEXT,
    retry_count             INTEGER         NOT NULL DEFAULT 0,
    preflight_result        VARCHAR(20),
    preflight_issues_json   TEXT,
    preflight_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_official_po_integration_request_order_revision UNIQUE (portal_order_id, revision_no),
    CONSTRAINT ck_official_po_integration_request_status CHECK (status IN
        ('PENDING', 'GENERATED', 'SUBMITTED', 'CONFIRMED', 'FAILED')),
    CONSTRAINT ck_official_po_integration_request_preflight_result CHECK (preflight_result IS NULL OR preflight_result IN
        ('PASS', 'WARNING', 'BLOCKED'))
);

CREATE INDEX idx_official_po_integration_request_portal_order_id ON official_po_integration_request (portal_order_id);

ALTER TABLE audit_event ALTER COLUMN event_type TYPE VARCHAR(50);

ALTER TABLE audit_event DROP CONSTRAINT ck_audit_event_type;
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_type CHECK (event_type IN (
    'ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED',
    'SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED',
    'ATTENTION_ADDED','ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED',
    'ORDER_DATE_CHANGED','ORDER_RETURNED_TO_DRAFT',
    'SUBMITTED_FOR_APPROVAL','ORDER_APPROVED','APPROVED_WITH_CHANGES','RETURNED_FOR_CORRECTION',
    'OFFICIAL_PO_INTEGRATION_REQUESTED','PRECHECK_COMPLETED'
));
