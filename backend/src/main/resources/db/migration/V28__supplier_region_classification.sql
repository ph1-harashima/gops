-- Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章):
-- Domestic/Overseas Foundation. FACT (re-confirmed this Phase, READ ONLY):
-- Legacy has no Country/Region field for Supplier - MS_COMM's generic
-- VAL_1..VAL_10 slots are repurposed per CATE_ID with no documented,
-- universal meaning (e.g. BusinessLogicUtil.getNewArrCode() reads VAL_10 as
-- an unrelated sequence number for MS_SUPPL rows) - there is no safe way to
-- derive Domestic/Overseas from existing Legacy data. This is therefore a
-- NEW, Portal-only, ADMIN-set classification - never read from or written
-- to Legacy, and NEVER consulted by RecommendedQtyCalculator/
-- OrderQuantityCalculator (explicit customer instruction: no "Domestic
-- formula"/"Overseas formula" may be invented this Phase).
--
-- Same shape/Uniqueness policy as manufacturer_channel (V21): Legacy READ
-- ONLY existence check for supplier_code/brand_code at write time (Service
-- layer), brand-specific row wins over supplier-only at Resolution time,
-- no further fallback (never guessed).
CREATE TABLE supplier_region_classification (
    id                      BIGSERIAL PRIMARY KEY,
    supplier_code           VARCHAR(10)     NOT NULL,
    brand_code              VARCHAR(10),
    region_classification   VARCHAR(10)     NOT NULL,
    is_active               BOOLEAN         NOT NULL DEFAULT true,
    created_by              VARCHAR(50)     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by              VARCHAR(50)     NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_supplier_region_classification_value CHECK (region_classification IN ('DOMESTIC', 'OVERSEAS'))
);

CREATE UNIQUE INDEX uq_supplier_region_classification_active_identity ON supplier_region_classification
    (supplier_code, COALESCE(brand_code, ''))
    WHERE is_active = true;
