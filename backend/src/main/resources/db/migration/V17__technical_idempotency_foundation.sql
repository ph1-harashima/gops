-- Prototype PostgreSQL only. Phase 8-L: Production Reliability Foundation -
-- Technical Idempotency Foundation (Production Readiness Audit §11-16).
--
-- Confirmed before adding this table that official_po_integration_request
-- (V9) cannot safely serve this purpose: its columns and status CHECK
-- constraint are Official-PO-specific (official_po_no, generated_file_key,
-- preflight_result, PENDING/GENERATED/SUBMITTED/CONFIRMED/FAILED), and its
-- UNIQUE constraint is scoped to (portal_order_id, revision_no) rather than
-- a generic (operation_type, idempotency_key) pattern. Reusing it for a
-- future Email/EDI send would force Official-PO-only columns onto rows that
-- have nothing to do with Official PO. A new, minimal, operation-agnostic
-- table is therefore justified (Phase 8-L §13 explicit instruction to check
-- this first).
--
-- This is a pure TECHNICAL Foundation: status is STARTED/SUCCEEDED/FAILED
-- only - never a Business Workflow State like PENDING_APPROVAL (§14). No
-- real External Side Effect (Email/EDI/Official PO Handoff) is wired to
-- this table this Phase - it exists so a *future* Phase implementing one of
-- those can claim exactly one attempt per idempotency_key without inventing
-- its own ad-hoc duplicate-prevention mechanism each time.
--
-- Concurrent-duplicate prevention (§15) is the UNIQUE constraint itself: two
-- simultaneous claim attempts for the same (operation_type, idempotency_key)
-- race on INSERT - exactly one succeeds, the other gets a constraint
-- violation the Service layer (IdempotencyService) interprets as "someone
-- else already claimed this", never a plain SELECT-then-INSERT check alone.
-- `version` (JPA @Version/Optimistic Locking) additionally protects the
-- retry-after-FAILED path (a concurrent lost-update on the same existing
-- row, distinct from the concurrent-INSERT race the UNIQUE constraint
-- already covers).

CREATE TABLE idempotent_operation (
    id                  BIGSERIAL PRIMARY KEY,
    operation_type      VARCHAR(50)   NOT NULL,
    business_key        VARCHAR(200)  NOT NULL,
    idempotency_key     VARCHAR(200)  NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'STARTED',
    attempt             INTEGER       NOT NULL DEFAULT 1,
    error_code          VARCHAR(50),
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotent_operation_type_key UNIQUE (operation_type, idempotency_key),
    CONSTRAINT ck_idempotent_operation_status CHECK (status IN ('STARTED', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_idempotent_operation_business_key ON idempotent_operation (operation_type, business_key);
