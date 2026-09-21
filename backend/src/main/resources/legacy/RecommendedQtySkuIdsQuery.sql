-- Stage 5E Targeted Remediation (RC-C, docs/real-data-audit/
-- gops-stage5e-targeted-remediation.md): Step 1 of the two-step
-- Candidate List / Stock-Sales List pagination redesign Stage 5D's RCA
-- called for ("page対象SKUを先に確定してから必要なSKUだけ...JOIN").
--
-- Stage 5D's EXPLAIN ANALYZE (against the real Production Snapshot)
-- confirmed the previous single-query design materialized EVERY row
-- matching the Brand/Supplier/keyword filter - current_stock, brand name,
-- supplier name, formula data, all of it - BEFORE the outer LIMIT/OFFSET
-- trimmed it down to one page. The two per-row cost centers were the
-- ms_comm brand/supplier NAME lookups and (to a much smaller, flat,
-- non-scaling degree) the latest_po derived table. This query is Step 1:
-- it resolves ONLY the item_cd values for one page, filtered/ordered
-- exactly as before, but never touches ms_comm or ms_formula at all -
-- those (and the human-readable brand/supplier names) are only fetched in
-- Step 2 (RecommendedQtyReadQuery.sql's item_cd-bound path), for the
-- small, already-paginated set Step 1 resolves.
--
-- Stage 5H Systematic Performance Remediation (RC-I, docs/real-data-audit/
-- gops-stage5h-systematic-performance-remediation.md): this is now the
-- "full" variant, used only when :supplierCode or any of :minStock/
-- :maxStock/:minSales/:maxSales is actually set - LegacyStockReadRepository
-- chooses RecommendedQtySkuIdsLeanQuery.sql instead (identical brandCode/
-- keyword/includeDeleted semantics, no latest_po/ms_stk joins) for the far
-- more common Brand-only or fully-unfiltered browse, where those joins
-- were confirmed pure overhead.
--
-- Kept from the base query, because both are cheap (confirmed via
-- EXPLAIN ANALYZE - current_stock's correlated subquery is well-indexed
-- on ms_stk.item_cd; the 'XX' aggregate row join is a primary-key lookup)
-- and because minStock/maxStock/minSales/maxSales (Stock/Sales List's own
-- 2 pure-numeric range filters) filter on these COMPUTED values, so Step 1
-- cannot skip them without breaking that filter:
--   - current_stock (PHISICAL_QTY, WH_CD IN ('4','5','6','7','8','10','11','12','15') -
--     UNCHANGED formula, Stage 3B/4)
--   - monthly_sales (agg.sold_qty)
-- latest_po is also kept (needed only for :supplierCode filtering) - its
-- own cost is a flat, once-per-query-execution ~600-800ms (Stage 5D
-- EXPLAIN ANALYZE), not per-row, so including it unconditionally here
-- (rather than a second, supplier-filter-only SQL variant) keeps this
-- file's semantics a direct match of the original query's WHERE clause
-- with no dynamic-SQL branching to maintain.
SELECT
    i.item_cd,
    i.brand_cd
FROM ms_item i
LEFT JOIN ms_stk agg
       ON agg.item_cd = i.item_cd AND agg.wh_cd = 'XX'
LEFT JOIN (
    SELECT d.item_cd, p.supplier_cd,
           ROW_NUMBER() OVER (PARTITION BY d.item_cd ORDER BY p.ordr_date DESC) AS rn
    FROM tr_po_dtl d
    JOIN tr_po p ON p.po_no = d.po_no
) latest_po
       ON latest_po.item_cd = i.item_cd AND latest_po.rn = 1
WHERE (:includeDeleted = TRUE OR i.del_flg IS NULL OR i.del_flg = 0)
  AND (:brandCode IS NULL OR i.brand_cd = :brandCode)
  AND (:supplierCode IS NULL OR latest_po.supplier_cd = :supplierCode)
  AND (:keyword IS NULL OR i.item_cd LIKE :keywordLike OR i.description LIKE :keywordLike)
  AND (:minStock IS NULL OR COALESCE((
        SELECT SUM(phys.stk_qty) FROM ms_stk phys
        WHERE phys.item_cd = i.item_cd
          AND phys.wh_cd IN ('4','5','6','7','8','10','11','12','15')
      ), 0) >= :minStock)
  AND (:maxStock IS NULL OR COALESCE((
        SELECT SUM(phys.stk_qty) FROM ms_stk phys
        WHERE phys.item_cd = i.item_cd
          AND phys.wh_cd IN ('4','5','6','7','8','10','11','12','15')
      ), 0) <= :maxStock)
  AND (:minSales IS NULL OR COALESCE(agg.sold_qty, 0) >= :minSales)
  AND (:maxSales IS NULL OR COALESCE(agg.sold_qty, 0) <= :maxSales)
ORDER BY i.brand_cd, i.item_cd
LIMIT :limit OFFSET :offset
