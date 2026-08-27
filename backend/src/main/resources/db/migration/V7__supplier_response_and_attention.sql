-- Prototype PostgreSQL only. Technical Design 5.3/5.4/5.5, Implementation Step 4.
-- Never applied to Legacy (Flyway is bound exclusively to prototypeDataSource -
-- see PrototypeFlywayConfig and the Safety Gate).

-- 1 Order = 1 current-state supplier_response header (Technical Design 5.3
-- decision: no append-only version table this Step; change HISTORY is
-- reconstructed from audit_event, only the CURRENT value lives here).
CREATE TABLE supplier_response (
    id              BIGSERIAL PRIMARY KEY,
    portal_order_id BIGINT          NOT NULL UNIQUE REFERENCES portal_order(id),
    response_date   DATE,
    response_note   TEXT,
    response_status VARCHAR(20)     NOT NULL DEFAULT 'PARTIAL'
                    CHECK (response_status IN ('PARTIAL', 'CONFIRMED')),
    received_by     VARCHAR(50),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ordered_qty / requested_delivery are Demo Send-time snapshots (Original
-- value the Supplier was actually told), immutable thereafter - initialized
-- by DemoSendService, never by this GET/PUT Supplier Response flow.
-- confirmed_qty deliberately has NO DEFAULT and IS NULLable: NULL = not yet
-- answered, 0 = an explicit "zero" answer (implementation instructions 11章
-- "0 と null" - the single most important Validation rule this Step).
CREATE TABLE supplier_response_detail (
    id                      BIGSERIAL PRIMARY KEY,
    supplier_response_id    BIGINT      NOT NULL REFERENCES supplier_response(id) ON DELETE CASCADE,
    portal_order_detail_id  BIGINT      NOT NULL REFERENCES portal_order_detail(id),
    ordered_qty             INTEGER     NOT NULL,
    confirmed_qty           INTEGER,
    requested_delivery      DATE,
    confirmed_delivery      DATE,
    response_note           TEXT,
    is_confirmed            BOOLEAN     NOT NULL DEFAULT false,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_supplier_response_detail UNIQUE (supplier_response_id, portal_order_detail_id)
);

CREATE INDEX idx_supplier_response_detail_response_id ON supplier_response_detail (supplier_response_id);

CREATE TABLE order_attention (
    id                      BIGSERIAL PRIMARY KEY,
    portal_order_id         BIGINT      NOT NULL REFERENCES portal_order(id),
    portal_order_detail_id  BIGINT      REFERENCES portal_order_detail(id),
    attention_type          VARCHAR(30) NOT NULL CHECK (attention_type IN
                            ('QUANTITY_CHANGED', 'DELIVERY_CHANGED', 'PARTIAL_CONFIRMATION',
                             'DATA_OUTDATED', 'OTHER_ATTENTION')),
    is_active               BOOLEAN     NOT NULL DEFAULT true,
    detected_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at             TIMESTAMPTZ,
    acknowledged_by         VARCHAR(50),
    acknowledged_at         TIMESTAMPTZ,
    note                    TEXT
);

CREATE INDEX idx_order_attention_order_id ON order_attention (portal_order_id);

-- Duplicate-ACTIVE prevention (implementation instructions 15章). Uses
-- COALESCE(portal_order_detail_id, -1) rather than the column directly:
-- Postgres treats NULL as distinct-from-itself in a plain unique index, so
-- two ACTIVE PARTIAL_CONFIRMATION rows for the same Order (which is
-- Order-level, portal_order_detail_id IS NULL by design) would NOT collide
-- and could duplicate without this normalization - a correctness gap in a
-- literal reading of Technical Design 5.5 caught while writing this
-- migration.
CREATE UNIQUE INDEX uq_order_attention_active
    ON order_attention (portal_order_id, COALESCE(portal_order_detail_id, -1), attention_type)
    WHERE is_active = true;
