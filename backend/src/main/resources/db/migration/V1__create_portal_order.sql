-- Prototype PostgreSQL only. NEVER applied to Legacy (see PrototypeFlywayConfig
-- and the Safety Gate - Flyway is explicitly bound only to prototypeDataSource).
-- Technical Design 5.1.

CREATE TABLE portal_order (
    id                      BIGSERIAL PRIMARY KEY,
    draft_no                VARCHAR(30)     NOT NULL,
    prototype_po_no         VARCHAR(30),
    supplier_code           VARCHAR(10)     NOT NULL,
    supplier_name_snapshot  VARCHAR(200)    NOT NULL,
    brand_code              VARCHAR(10)     NOT NULL,
    brand_name_snapshot     VARCHAR(200)    NOT NULL,
    order_date              DATE            NOT NULL,
    requested_delivery      DATE,
    currency                VARCHAR(10),
    status                  VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    remark                  TEXT,
    total_qty               INTEGER         NOT NULL DEFAULT 0,
    total_amount            NUMERIC(14,2)   NOT NULL DEFAULT 0,
    data_source             VARCHAR(20)     NOT NULL DEFAULT 'DEMO_LEGACY',
    created_by              VARCHAR(50)     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by              VARCHAR(50)     NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version                 INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT uq_portal_order_draft_no UNIQUE (draft_no),
    CONSTRAINT ck_portal_order_status CHECK (status IN
        ('DRAFT','READY_TO_ORDER','SENT','AWAITING_SUPPLIER','SUPPLIER_CONFIRMED','COMPLETED')),
    CONSTRAINT ck_portal_order_data_source CHECK (data_source IN ('DEMO_LEGACY','LEGACY'))
);

CREATE UNIQUE INDEX uq_portal_order_prototype_po_no ON portal_order (prototype_po_no)
    WHERE prototype_po_no IS NOT NULL;
CREATE INDEX idx_portal_order_status ON portal_order (status);
CREATE INDEX idx_portal_order_supplier_code ON portal_order (supplier_code);
CREATE INDEX idx_portal_order_brand_code ON portal_order (brand_code);
CREATE INDEX idx_portal_order_order_date ON portal_order (order_date);
