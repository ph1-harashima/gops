-- Stage 5H Systematic Performance Remediation (RC-H, docs/real-data-audit/
-- gops-stage5h-systematic-performance-remediation.md): the ONLY thing
-- SupplierMasterService.brandsBySupplier() needs from Legacy is which
-- (Supplier, Brand) pairs exist - it never reads current_stock,
-- Recommended Qty, or any calc4/FormulaParser output. Stage 5G's audit
-- found brandsBySupplier() deriving this from
-- OrderCandidateService.findOrderCandidates(null,null,null) - the full,
-- unpaginated, 116,842-SKU calc4-evaluating catalog method Dashboard used
-- before RC-A - just to read 2 of its ~20 fields. This query replaces
-- that: DISTINCT (supplier_cd, brand_cd) pairs only, computed entirely in
-- SQL, no ms_formula, no ms_stk (other than what latest_po itself needs).
--
-- Supplier is still derived the same documented way as everywhere else in
-- this codebase (RecommendedQtyReadQuery.sql's own header comment: MS_ITEM
-- has no supplier_cd column in Legacy, so the most recent TR_PO/TR_PO_DTL
-- row is used) - same latest_po derived table shape, same
-- ROW_NUMBER()-over-tr_po_dtl pattern, same flat ~500-800ms one-time cost
-- (Stage 5D/5G EXPLAIN ANALYZE) regardless of catalog size, no Production
-- DB index change (tr_po_dtl.item_cd is still unindexed).
--
-- Deliberately does NOT join ms_comm for the Brand display name (unlike
-- RecommendedQtyReadQuery.sql, which needs it per-row for the Candidate
-- List's own display) - Stage 5D/5G both confirmed ms_comm's join plan
-- costs ~1ms per outer row despite the correctly-structured
-- (CATE_ID, CODE_ID) composite PRIMARY KEY (confirmed again in this
-- Stage's own EXPLAIN ANALYZE: joining ms_comm here directly costs
-- ~27s for this query's ~28,000 pre-DISTINCT row scale). The caller
-- resolves brand_name from the existing bulk
-- LegacyStockReadRepository.findAllBrandNames() map instead (already used
-- by DashboardService, RC-A) - one shared, already-proven bulk lookup,
-- not a second per-row Legacy join.
SELECT DISTINCT latest_po.supplier_cd, i.brand_cd
FROM ms_item i
JOIN (
    SELECT d.item_cd, p.supplier_cd,
           ROW_NUMBER() OVER (PARTITION BY d.item_cd ORDER BY p.ordr_date DESC) AS rn
    FROM tr_po_dtl d
    JOIN tr_po p ON p.po_no = d.po_no
) latest_po
       ON latest_po.item_cd = i.item_cd AND latest_po.rn = 1
WHERE (i.del_flg IS NULL OR i.del_flg = 0)
  AND latest_po.rn = 1
  AND latest_po.supplier_cd IS NOT NULL
  AND i.brand_cd IS NOT NULL
