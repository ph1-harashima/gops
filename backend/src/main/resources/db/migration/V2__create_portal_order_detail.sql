-- Prototype PostgreSQL only. Technical Design 5.2.

CREATE TABLE portal_order_detail (
    id                          BIGSERIAL PRIMARY KEY,
    portal_order_id             BIGINT          NOT NULL REFERENCES portal_order(id) ON DELETE CASCADE,
    line_no                     INTEGER         NOT NULL,
    sku                         VARCHAR(30)     NOT NULL,
    item_name_snapshot          VARCHAR(200)    NOT NULL,
    recommended_qty             INTEGER         NOT NULL,
    order_qty                   INTEGER         NOT NULL,
    unit_price                  NUMERIC(13,5),
    amount                      NUMERIC(14,2)   NOT NULL DEFAULT 0,
    current_stock_snapshot      INTEGER,
    safety_stock_snapshot       INTEGER,
    open_po_snapshot            INTEGER,
    recent_sales_snapshot       INTEGER,
    lead_time_snapshot          VARCHAR(10),
    item_status_snapshot        VARCHAR(30),
    data_source                 VARCHAR(20)     NOT NULL DEFAULT 'DEMO_LEGACY',
    is_removed                  BOOLEAN         NOT NULL DEFAULT false,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_portal_order_detail_line UNIQUE (portal_order_id, line_no),
    CONSTRAINT ck_portal_order_detail_order_qty CHECK (order_qty >= 0),
    CONSTRAINT ck_portal_order_detail_data_source CHECK (data_source IN ('DEMO_LEGACY','LEGACY'))
);

CREATE INDEX idx_portal_order_detail_order_id ON portal_order_detail (portal_order_id);
CREATE INDEX idx_portal_order_detail_sku ON portal_order_detail (sku);
