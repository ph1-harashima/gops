-- Stage 5K (docs/real-data-audit/gops-stage5k-dashboard-read-model-implementation.md):
-- Portal DB Read Model for Dashboard/Brand List's Legacy-derived KPIs
-- (candidateCount, outOfStockCount, longTermOutOfStockCount, overall and
-- per-Brand), computed by an unattended background Refresh job that runs
-- the exact same, unmodified calc4 pipeline over the exact same
-- ~47,280-SKU population (Stage 5I/5J - no population narrowing, no
-- meaning change). The Dashboard/Brand List HTTP request path never
-- invokes calc4 - it reads a row here instead. Design carried over
-- verbatim from Stage 5J §6, naming/typing per the same convention
-- V32/V33 (sku_expected_restock) already established.

-- One row per Refresh attempt (history + the source of the "current" pointer).
CREATE TABLE dashboard_refresh_run (
    id                          BIGSERIAL PRIMARY KEY,
    status                      VARCHAR(20)   NOT NULL,   -- RUNNING / SUCCEEDED / FAILED
    trigger_type                VARCHAR(20)   NOT NULL,   -- SCHEDULED / STARTUP / MANUAL
    calculation_started_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    calculation_completed_at    TIMESTAMPTZ,
    evaluated_item_count        INTEGER,
    candidate_count              INTEGER,                 -- overall, duplicated from dashboard_legacy_aggregate for quick health checks
    formula_error_count          INTEGER       NOT NULL DEFAULT 0,  -- RC-D observability (Stage 5J §16/§17, Addendum §11) - stays near-0 by construction
    error_message                TEXT,
    initiated_by                 VARCHAR(50),              -- ADMIN username for MANUAL; NULL otherwise
    CONSTRAINT ck_dashboard_refresh_run_status
        CHECK (status IN ('RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT ck_dashboard_refresh_run_trigger_type
        CHECK (trigger_type IN ('SCHEDULED','STARTUP','MANUAL'))
);

CREATE INDEX idx_dashboard_refresh_run_status_started
    ON dashboard_refresh_run (status, calculation_started_at);

-- Enforces single-flight at the DB level too (defense in depth alongside
-- the Postgres advisory lock, DashboardRefreshService): at most one
-- RUNNING row ever.
CREATE UNIQUE INDEX uq_dashboard_refresh_run_one_running
    ON dashboard_refresh_run ((status))
    WHERE status = 'RUNNING';

-- Overall Aggregate - one row per SUCCEEDED refresh.
CREATE TABLE dashboard_legacy_aggregate (
    refresh_run_id                BIGINT PRIMARY KEY
        REFERENCES dashboard_refresh_run (id) ON DELETE CASCADE,
    candidate_count                INTEGER NOT NULL,
    out_of_stock_count             INTEGER NOT NULL,
    long_term_out_of_stock_count   INTEGER NOT NULL
);

-- Brand Aggregate - one row per (refresh, Brand) per SUCCEEDED refresh.
-- Surrogate BIGSERIAL id (rather than a composite PK on
-- (refresh_run_id, brand_code), Stage 5J §6's literal proposal) - this
-- codebase's existing Portal domain entities have no @EmbeddedId/@IdClass
-- precedent anywhere (checked), so a surrogate id plus a UNIQUE constraint
-- keeps this table on the same simple single-column-@Id JPA shape every
-- other entity here already uses, with an identical uniqueness guarantee.
CREATE TABLE dashboard_brand_legacy_aggregate (
    id                              BIGSERIAL PRIMARY KEY,
    refresh_run_id                 BIGINT NOT NULL
        REFERENCES dashboard_refresh_run (id) ON DELETE CASCADE,
    brand_code                     VARCHAR(10) NOT NULL,  -- Legacy-sourced, not a Portal FK (no Brand master table)
    -- Captured at Refresh time from the same ms_comm CATE_ID='MS_BRAND'
    -- bulk lookup DashboardService's old live path always used
    -- (LegacyStockReadRepository.findAllBrandNames) - stored here so the
    -- Dashboard/Brand List REQUEST path never touches Legacy at all (Stage
    -- 5K §11/§12 acceptance: "Legacy heavy query = 0"), not just calc4.
    brand_name                      VARCHAR(200),
    candidate_count                 INTEGER NOT NULL,
    out_of_stock_count              INTEGER NOT NULL,
    long_term_out_of_stock_count    INTEGER NOT NULL,
    CONSTRAINT uq_dashboard_brand_legacy_aggregate_run_brand
        UNIQUE (refresh_run_id, brand_code)
);

CREATE INDEX idx_dashboard_brand_legacy_aggregate_run
    ON dashboard_brand_legacy_aggregate (refresh_run_id);

-- Atomic "current version" pointer - single row, updated with one UPDATE/
-- UPSERT statement. Never written by anything except a successful
-- refresh's final activation step (DashboardRefreshService).
CREATE TABLE dashboard_aggregate_current (
    singleton                   BOOLEAN NOT NULL PRIMARY KEY DEFAULT true,
    active_refresh_run_id       BIGINT NOT NULL
        REFERENCES dashboard_refresh_run (id),
    activated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_dashboard_aggregate_current_singleton CHECK (singleton)
);
-- Starts empty until the first successful refresh (Startup Behavior,
-- DashboardRefreshStartupRunner) - INSERT ... ON CONFLICT (singleton) DO
-- UPDATE is the activation statement, a single atomic write.
