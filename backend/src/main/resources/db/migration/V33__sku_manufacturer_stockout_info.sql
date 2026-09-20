-- Post-Freeze Business Refinement 2 (docs/gops-manufacturer-stockout-information-management.md):
-- broadens sku_expected_restock (V32) from "just a restock date" into
-- "Manufacturer Stockout Information" - extends the EXISTING table in
-- place rather than creating a parallel current-state table (explicit
-- instruction this round), since it is still exactly one row per SKU with
-- the same update-in-place identity as before. V32 itself is left
-- unmodified.
--
-- System Information (Legacy READ ONLY: Current Stock/Open PO/Expected
-- Arrival/Sales/Lead Time) and Manufacturer Stockout Information (this
-- table) are two distinct data sources by design (requirements doc §3) -
-- nothing here reads from or writes to Legacy.
ALTER TABLE sku_expected_restock ADD COLUMN stockout_status VARCHAR(30);
ALTER TABLE sku_expected_restock ADD CONSTRAINT ck_sku_expected_restock_status
    CHECK (stockout_status IS NULL OR stockout_status IN ('STOCKOUT', 'LONG_TERM_STOCKOUT', 'RESOLVED'));

-- Nullable and deliberately NOT defaulted to 0: "confirmed short by some
-- amount" (a real Supplier Response difference) and "heard it's long-term
-- stockout with no quantity discussed at all" (e.g. a phone call before
-- any order existed) are different states - requirements doc §7.
ALTER TABLE sku_expected_restock ADD COLUMN shortage_qty INTEGER;
ALTER TABLE sku_expected_restock ADD CONSTRAINT ck_sku_expected_restock_shortage_qty
    CHECK (shortage_qty IS NULL OR shortage_qty >= 0);

-- Distinct from updated_at (when G-OPS was updated) - when the Manufacturer
-- actually communicated the information (requirements doc §9).
ALTER TABLE sku_expected_restock ADD COLUMN information_received_date DATE;

ALTER TABLE sku_expected_restock ADD COLUMN contact_method VARCHAR(20);
ALTER TABLE sku_expected_restock ADD CONSTRAINT ck_sku_expected_restock_contact_method
    CHECK (contact_method IS NULL OR contact_method IN ('PHONE', 'EMAIL', 'ORDER_RESPONSE', 'OTHER'));

-- Business-facing History (requirements doc §11) - deliberately separate
-- from audit_event's free-text Before/After summary (Technical Audit,
-- unchanged, still written on every change via the existing
-- SKU_EXPECTED_RESTOCK_CHANGED event type - no new event type needed,
-- same table/aggregate root). One row per change, snapshotting the FULL
-- resulting state, so a screen can render a timeline
-- ("09/20 長期欠品/未定/電話 -> 10/05 長期欠品/11-15/メール -> ...")
-- directly without parsing audit_event text. Never updated or deleted -
-- an append-only ledger, including the RESOLVED transition (requirements
-- doc §22: resolving never deletes the record's history).
CREATE TABLE sku_manufacturer_stockout_history (
    id                          BIGSERIAL PRIMARY KEY,
    sku_code                    VARCHAR(50)     NOT NULL,
    stockout_status              VARCHAR(30),
    expected_restock_date        DATE,
    is_unknown                   BOOLEAN         NOT NULL DEFAULT false,
    shortage_qty                  INTEGER,
    information_received_date    DATE,
    contact_method                VARCHAR(20),
    memo                          VARCHAR(500),
    recorded_by                   VARCHAR(50)     NOT NULL,
    recorded_at                   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_sku_mfr_stockout_history_status
        CHECK (stockout_status IS NULL OR stockout_status IN ('STOCKOUT', 'LONG_TERM_STOCKOUT', 'RESOLVED')),
    CONSTRAINT ck_sku_mfr_stockout_history_shortage_qty
        CHECK (shortage_qty IS NULL OR shortage_qty >= 0),
    CONSTRAINT ck_sku_mfr_stockout_history_contact_method
        CHECK (contact_method IS NULL OR contact_method IN ('PHONE', 'EMAIL', 'ORDER_RESPONSE', 'OTHER')),
    CONSTRAINT ck_sku_mfr_stockout_history_unknown_xor_date
        CHECK (NOT (is_unknown = true AND expected_restock_date IS NOT NULL))
);

CREATE INDEX idx_sku_mfr_stockout_history_sku_recorded_at
    ON sku_manufacturer_stockout_history (sku_code, recorded_at);
