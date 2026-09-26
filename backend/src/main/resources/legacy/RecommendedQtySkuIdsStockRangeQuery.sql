-- RecommendedQty Production-scale performance remediation (§ "REMEDIATION
-- REQUIRED" item carried over from Warehouse Stock's own Same-Pattern Risk
-- Measurements, docs/real-data-audit/gops-warehouse-stock-production-scale-
-- remediation.md §12): variant of RecommendedQtySkuIdsQuery.sql ("full")
-- used when a minStock/maxStock/minSales/maxSales filter is set but
-- :supplierCode is NOT - LegacyStockReadRepository chooses between the
-- three variants (Lean/StockRange/Full).
--
-- Root cause (this remediation's own EXPLAIN ANALYZE + timed decomposition
-- against the real Production Snapshot, read-only): the "full" variant's
-- previously-measured 7.35s (list) / 5.51s (count) is NOT primarily caused
-- by minStock/maxStock's own correlated subquery (individually cheap and
-- well-indexed on ms_stk.item_cd, confirmed via EXPLAIN ANALYZE - the
-- correlated subquery already correctly short-circuits via `:minStock IS
-- NULL OR ...` and costs ~0.08ms per invocation even across ~47,280
-- invocations for the fully-unfiltered case). The `latest_po` LEFT JOIN
-- (needed only for :supplierCode) is UNCONDITIONAL in the full query
-- regardless of which specific filter triggered the "full" path - decomposed
-- timing confirmed it alone costs ~1s even when :supplierCode is NULL and
-- therefore provably unused, on top of whatever minStock/maxStock/minSales
-- itself costs. This variant removes that JOIN and its now-dead
-- :supplierCode predicate entirely for the case where it can never match -
-- a provably equivalent rewrite for this variant's own calling context (the
-- same reasoning RecommendedQtySkuIdsLeanQuery.sql's own header comment
-- already uses), not a behavior/semantics change.
--
-- Two alternative rewrites of the minStock/maxStock correlated subquery
-- itself (a pre-aggregated `GROUP BY item_cd` derived table, both
-- unconditional and restricted-to-active-items forms) were tried and timed
-- against the same Production Snapshot and found SLOWER (12-13s and ~6.2s
-- respectively) than the existing correlated-subquery approach - confirmed
-- via `SHOW STATUS LIKE 'Created_tmp%'` that the aggregation spills to a
-- disk-backed temp table under the Legacy server's own tmp_table_size/
-- max_heap_table_size (16MB each) - a Legacy DB server configuration this
-- remediation has no authority to change and does not attempt to. Kept the
-- existing correlated-subquery shape for minStock/maxStock/minSales as the
-- empirically faster, safe option.
SELECT
    i.item_cd,
    i.brand_cd
FROM ms_item i
LEFT JOIN ms_stk agg
       ON agg.item_cd = i.item_cd AND agg.wh_cd = 'XX'
WHERE (:includeDeleted = TRUE OR i.del_flg IS NULL OR i.del_flg = 0)
  AND (:brandCode IS NULL OR i.brand_cd = :brandCode)
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
