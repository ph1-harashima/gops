-- Confirmed Business Rules BR-07/BR-08
-- (docs/gulliver-20260917-confirmed-business-rules.md): Official PO No. is
-- no longer staff-entered free text - it is auto-numbered by G-OPS as
-- {SupplierShortCode 3 chars}{BrandShortCode 3 chars}{3-digit sequence},
-- the sequence counted per Supplier x Brand combination.
--
-- FACT (READ ONLY, re-confirmed this Phase): Legacy `ms_comm`
-- (MS_SUPPL/MS_BRAND) has no 3-character abbreviation column - only the
-- existing longer `code_id` (e.g. SUP_ALPHA) and a free-text `code_name`
-- (company/brand display name). The 3-character abbreviation itself is a
-- NEW Business Concept Gulliver has not yet registered anywhere in Legacy.
-- Same situation as supplier_region_classification (V28): a Portal-only
-- Master an ADMIN populates with Gulliver's own decided values - G-OPS
-- itself never generates or infers an abbreviation.
CREATE TABLE official_po_short_code (
    id              BIGSERIAL PRIMARY KEY,
    code_type       VARCHAR(10)     NOT NULL,
    business_code   VARCHAR(10)     NOT NULL,
    short_code      VARCHAR(3)      NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    created_by      VARCHAR(50)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50)     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_official_po_short_code_type CHECK (code_type IN ('SUPPLIER', 'BRAND')),
    CONSTRAINT ck_official_po_short_code_length CHECK (char_length(short_code) = 3)
);

-- One ACTIVE short code per (code_type, business_code) - same "active
-- uniqueness only" policy as supplier_region_classification (V28), so a
-- prior mistaken entry can be deactivated and replaced without a hard
-- delete (Audit history is preserved).
CREATE UNIQUE INDEX uq_official_po_short_code_active_identity ON official_po_short_code
    (code_type, business_code)
    WHERE is_active = true;

-- BR-08: the 3-digit sequence, counted independently per Supplier x Brand
-- combination. A single-row-per-key UPSERT (see OfficialPoSequenceService)
-- takes an atomic Postgres row lock on the targeted key only - concurrent
-- allocations for DIFFERENT Supplier x Brand pairs never block each other,
-- while concurrent allocations for the SAME pair are strictly serialized
-- (never a bare "SELECT MAX(...) + 1" race).
CREATE TABLE official_po_sequence (
    supplier_code   VARCHAR(10)     NOT NULL,
    brand_code      VARCHAR(10)     NOT NULL,
    next_seq        INTEGER         NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    PRIMARY KEY (supplier_code, brand_code)
);
