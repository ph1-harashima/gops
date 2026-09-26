-- COUNT companion to RecommendedQtySkuIdsStockRangeQuery.sql - see that
-- file's own header comment. Identical WHERE clause, no ORDER BY/LIMIT, no
-- latest_po (same reasoning: provably unused when :supplierCode is NULL,
-- which is guaranteed whenever this variant is chosen).
SELECT COUNT(*)
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
