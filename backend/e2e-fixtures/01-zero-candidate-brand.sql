-- E2E-only deterministic fixture (E2E DB Isolation Audit, Part 2/3): a
-- Brand with a Master entry but zero order candidates, used by
-- order-candidates-brand-entry.spec.ts to verify the "候補0件も表示"
-- toggle. Replaces a prior dependency on "ACT"/"HNK" - Production Snapshot
-- data (docs/real-data-audit/gops-stage5b-production-like-snapshot-
-- environment.md) that only ever existed in the shared Demo gsys_portal by
-- historical accident, was never part of demo-data/'s seed, and structurally
-- cannot exist in a freshly-reset E2E-only database.
--
-- Deliberately NOT part of demo-data/02-seed.sql: this Brand/Item pair is a
-- purely synthetic fixture with no realistic stock/sales meaning (0 stock,
-- 0 standard) - it exists only to make candidateCount=0 deterministic, so
-- it does not belong in the environment shown to a customer during a Demo.
-- Applied ONLY by backend/e2e-db-reset.sh, layered on top of the SAME
-- demo-data/01-schema.sql + 02-seed.sql base every Demo environment also
-- uses - legacy_demo (Demo) never receives this file, only legacy_demo_e2e
-- does.
--
-- Why this is guaranteed to compute candidateCount=0 (verified by reading
-- backend/src/main/resources/legacy/DashboardCandidateInputsQuery.sql and
-- RecommendedQtyCalculator/OverseasRecommendedQtyStrategy - no production
-- code was changed to make this true, this fixture is simply shaped to fit
-- the existing calculation): with no ms_formula row, the Default 4EU
-- formula runs; with no 'XX' ms_stk aggregate row, stk_standard resolves to
-- 0 via COALESCE regardless of the item's actual current_stock - and every
-- calc1-4 step resolves non-positive whenever stk_standard=0, so
-- recommendedQty is always 0 and this item is never counted as a
-- candidate. It DOES still appear in dashboard_brand_legacy_aggregate
-- (rather than being entirely absent), because
-- DashboardStockAggregateQuery.sql's per-Brand GROUP BY runs over every
-- active ms_item row unconditionally, independent of candidateCount.
--
-- Only 3 rows total, on purpose (no Supplier/PO/Order beyond this) - Brand
-- Master + one Item + one physical-warehouse stock row is the minimum that
-- satisfies the business condition under test.
--
-- The ms_stk row (found necessary via a real E2E run, not assumed up
-- front): giving the item SOME positive physical stock (wh_cd='4',
-- stk_qty=10) - while still deliberately withholding an 'XX' aggregate row,
-- so stk_standard stays 0 via COALESCE - keeps candidateCount at 0 (the
-- calc1-4 chain always resolves non-positive when stk_standard=0,
-- regardless of current_stock) while ALSO making outOfStockCount/
-- longTermOutOfStockCount both 0. Without this, a zero-stock item makes
-- BR_ZERO look 100% out-of-stock, which the frontend's own pre-existing
-- isZeroActivityBrand() check (DashboardPage.tsx - candidateCount==0 AND
-- outOfStockCount==0 AND longTermOutOfStockCount==0 AND ...) then correctly
-- does NOT treat as "zero activity", so the Dashboard's OWN Brand Breakdown
-- table (a second surface fed by this exact same
-- dashboard_brand_legacy_aggregate data, entirely separate from the Order
-- Candidates Brand List this fixture was designed for) shows BR_ZERO
-- un-hidden by default - and since insertion order happened to put it
-- first, dashboard-deep-links.spec.ts's own "click the first Brand row"
-- Scenario landed on it instead of a real Brand, breaking a wholly
-- unrelated test. This row makes BR_ZERO satisfy that pre-existing
-- (production, unmodified) zero-activity condition on both surfaces.

INSERT INTO ms_comm (cate_id, code_id, cate_name, code_name, del_flg, create_datetime, update_datetime)
VALUES ('MS_BRAND','BR_ZERO','Brand Master','ZEROSTOCK',b'0',NOW(),NOW());

INSERT INTO ms_item (item_cd, brand_cd, description, del_flg, create_datetime, update_datetime)
VALUES ('ZS-ITEM-001','BR_ZERO','Zero Candidate Fixture Item (E2E only)',b'0',NOW(),NOW());

INSERT INTO ms_stk (wh_cd, item_cd, pri_wh_flag, stk_qty, del_flg, create_datetime, update_datetime)
VALUES ('4','ZS-ITEM-001',b'1',10,b'0',NOW(),NOW());
