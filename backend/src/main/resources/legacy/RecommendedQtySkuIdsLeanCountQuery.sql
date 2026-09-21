-- Stage 5H Systematic Performance Remediation (RC-I): COUNT companion to
-- RecommendedQtySkuIdsLeanQuery.sql - see that file's own header comment
-- for when this is chosen over the full RecommendedQtySkuIdsCountQuery.sql.
-- This Stage's own EXPLAIN ANALYZE (unfiltered, against the Production
-- Snapshot) measured ~1142ms with the latest_po/ms_stk joins present vs
-- ~19.5ms without them for this COUNT alone (the COUNT has no ORDER BY to
-- force a filesort, so removing the joins here is close to free).
SELECT COUNT(*)
FROM ms_item i
WHERE (:includeDeleted = TRUE OR i.del_flg IS NULL OR i.del_flg = 0)
  AND (:brandCode IS NULL OR i.brand_cd = :brandCode)
  AND (:keyword IS NULL OR i.item_cd LIKE :keywordLike OR i.description LIKE :keywordLike)
