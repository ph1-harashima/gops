-- Warehouse Stock Production-Scale Remediation (docs/real-data-audit/
-- gops-warehouse-stock-production-scale-remediation.md): Step 1 of a
-- two-step pagination, the same "resolve page keys cheaply first, fetch
-- detail only for that page" shape Stage 5E's RC-C already established
-- for Candidate List/Stock-Sales (RecommendedQtySkuIdsQuery.sql).
--
-- RCA finding (docs/real-data-audit/
-- gops-warehouse-stock-production-scale-performance-rca.md): the
-- previous single-query design's EXPLAIN plan showed "Using temporary;
-- Using filesort" because MySQL cannot avoid sorting the full
-- ms_item x ms_stk joined result (i.brand_cd, s.item_cd, s.wh_cd has no
-- covering index across this join) before LIMIT/OFFSET can apply. No
-- index change is available this Stage (Production Legacy schema/index
-- change is out of scope) - the only lever left is shrinking what has to
-- be sorted. This query is identical in join/filter/ORDER BY shape to
-- the old single query, but selects ONLY the three columns needed to
-- resolve one page's ordered (brand_cd, item_cd, wh_cd) keys - never
-- item description or the ms_comm brand-name join, both free-text/wider
-- columns that inflate the filesort's per-row footprint without being
-- needed until Step 2 fetches detail for just this page's already-small
-- key set.
SELECT
    i.brand_cd,
    s.item_cd,
    s.wh_cd
FROM ms_item i
JOIN ms_stk s ON s.item_cd = i.item_cd
WHERE s.wh_cd <> 'XX'
  AND (i.del_flg IS NULL OR i.del_flg = 0)
  AND (s.del_flg IS NULL OR s.del_flg = 0)
  AND (:skuKeyword IS NULL OR s.item_cd LIKE :skuKeywordLike OR i.description LIKE :skuKeywordLike)
  AND (:brandCode IS NULL OR i.brand_cd = :brandCode)
  AND (:whCode IS NULL OR s.wh_cd = :whCode)
  AND (:minQty IS NULL OR s.stk_qty >= :minQty)
  AND (:maxQty IS NULL OR s.stk_qty <= :maxQty)
ORDER BY i.brand_cd, s.item_cd, s.wh_cd
LIMIT :limit OFFSET :offset
