-- Stage 5H Systematic Performance Remediation (RC-I, docs/real-data-audit/
-- gops-stage5h-systematic-performance-remediation.md): lean variant of
-- RecommendedQtySkuIdsQuery.sql, used ONLY when none of supplierCode/
-- minStock/maxStock/minSales/maxSales is set (LegacyStockReadRepository
-- chooses between the two - same brandCode/keyword/includeDeleted
-- semantics, same ORDER BY, same pagination contract; the omitted
-- joins/predicates below are always no-ops in that case, so this is a
-- provably equivalent rewrite, not a behavior change).
--
-- Stage 5G's audit (EXPLAIN ANALYZE against the Production Snapshot)
-- found the unfiltered ("all Brands") Candidate List / Stock-Sales List
-- measured ~3.3-3.6s, well above the ~1.5-1.8s Stage 5E measured for a
-- single filtered Brand - because RecommendedQtySkuIdsQuery.sql's
-- `latest_po` LEFT JOIN (needed only for the :supplierCode filter) is
-- unconditional: with no Brand filter to narrow the driving `ms_item`
-- scan first, MySQL's optimizer builds a hash join against the full,
-- freshly-materialized 183,480-row `latest_po` derived table over ~47,280
-- active items BEFORE the outer ORDER BY/LIMIT can trim it to one page
-- (confirmed via this Stage's own EXPLAIN ANALYZE: ~2062ms with the join
-- present vs ~1051ms without it, for an identical unfiltered page-1
-- request). The `ms_stk agg` join (needed only for :minSales/:maxSales)
-- is cheap per-row on its own but is also dropped here since it is
-- provably unused when those filters are inactive too.
--
-- No Production DB index change - this is a query-shape change only, and
-- ORDER BY/pagination/row semantics are identical to the full query for
-- every row this variant can ever be asked to return.
SELECT
    i.item_cd,
    i.brand_cd
FROM ms_item i
WHERE (:includeDeleted = TRUE OR i.del_flg IS NULL OR i.del_flg = 0)
  AND (:brandCode IS NULL OR i.brand_cd = :brandCode)
  AND (:keyword IS NULL OR i.item_cd LIKE :keywordLike OR i.description LIKE :keywordLike)
ORDER BY i.brand_cd, i.item_cd
LIMIT :limit OFFSET :offset
