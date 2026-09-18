-- Gap Analysis C-2/C-4 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
-- Document lifecycle - ACTIVE/SUPERSEDED/CANCELLED - entirely SEPARATE from
-- the existing Integration Status axis (PENDING/GENERATED/SUBMITTED/
-- CONFIRMED/FAILED, which stays exclusively about the Excel -> Import
-- Folder -> G-SYS hand-off). At most one row per portal_order_id should
-- ever be ACTIVE at a time in normal operation (enforced in the Service
-- layer via Reissue/Cancel, not by a DB constraint - a partial unique index
-- would reject legitimate historical data from before this column existed,
-- since every pre-existing row defaults to ACTIVE below).
ALTER TABLE official_po_integration_request
    ADD COLUMN lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN lifecycle_reason TEXT,
    ADD COLUMN lifecycle_changed_by VARCHAR(50),
    ADD COLUMN lifecycle_changed_at TIMESTAMPTZ;
