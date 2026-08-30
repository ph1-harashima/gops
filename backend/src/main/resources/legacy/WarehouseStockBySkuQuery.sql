-- Warehouse Stock Detail (Phase 8-G 10章, Drawer/Expand form - "全Warehouse
-- のQty" for one SKU, no new Route). Same wh_cd <> 'XX' exclusion and
-- non-join constraint as WarehouseStockListQuery.sql.
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
  AND s.item_cd = :itemCd
  AND (i.del_flg IS NULL OR i.del_flg = 0)
  AND (s.del_flg IS NULL OR s.del_flg = 0)
ORDER BY s.wh_cd
