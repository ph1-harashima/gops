-- Warehouse Stock List (Phase 8-G 8章/9章). wh_cd = 'XX' is Legacy's own
-- aggregate/primary row (Const.MS_STK_WH_CD_PRI, confirmed Phase 0.5/8-C),
-- not a real physical warehouse - deliberately excluded here so this screen
-- only ever shows genuine per-warehouse rows.
--
-- Deliberately does NOT join tr_arr/tr_po/tr_inv anywhere (Phase 8-G 11章's
-- most important constraint - Arrival and Warehouse Stock must never be
-- joined).
SELECT
    s.wh_cd,
    s.item_cd,
    i.description AS item_name,
    i.brand_cd,
    b.code_name   AS brand_name,
    s.stk_qty,
    s.update_datetime
FROM ms_stk s
JOIN ms_item i ON i.item_cd = s.item_cd
LEFT JOIN ms_comm b ON b.cate_id = 'MS_BRAND' AND b.code_id = i.brand_cd
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
