-- Warehouse Stock Production-Scale Remediation (docs/real-data-audit/
-- gops-warehouse-stock-production-scale-remediation.md): Step 2 of the
-- two-step pagination (WarehouseStockPageKeysQuery.sql is Step 1) -
-- fetches full display detail for every physical-warehouse row of the
-- (small) set of item_cd values Step 1's page resolved. Deliberately
-- NOT filtered to the exact (item_cd, wh_cd) pair set here (that exact
-- filtering, and restoring Step 1's own ordering, happens in Java -
-- WarehouseStockReadRepository's own comment explains why): this keeps
-- the SQL static and simple, and the item_cd-only IN-list is already
-- tightly bounded (at most one page's worth of distinct SKUs, each with
-- only a handful of real warehouse rows - Stage 2's own "~13 warehouse
-- rows / SKU" figure), never a Production-scale full-table cost.
--
-- Same SELECT list, joins, and wh_cd <> 'XX' exclusion as
-- WarehouseStockBySkuQuery.sql (the existing single-SKU Detail query) -
-- this is that same query's shape, widened from one item_cd to a
-- bounded IN-list.
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
  AND s.item_cd IN (:itemCodes)
  AND (i.del_flg IS NULL OR i.del_flg = 0)
  AND (s.del_flg IS NULL OR s.del_flg = 0)
