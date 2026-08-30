-- COUNT companion to WarehouseStockListQuery.sql - same WHERE clause.
SELECT COUNT(*)
FROM ms_stk s
JOIN ms_item i ON i.item_cd = s.item_cd
WHERE s.wh_cd <> 'XX'
  AND (i.del_flg IS NULL OR i.del_flg = 0)
  AND (s.del_flg IS NULL OR s.del_flg = 0)
  AND (:skuKeyword IS NULL OR s.item_cd LIKE :skuKeywordLike OR i.description LIKE :skuKeywordLike)
  AND (:brandCode IS NULL OR i.brand_cd = :brandCode)
  AND (:whCode IS NULL OR s.wh_cd = :whCode)
  AND (:minQty IS NULL OR s.stk_qty >= :minQty)
  AND (:maxQty IS NULL OR s.stk_qty <= :maxQty)
