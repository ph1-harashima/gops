-- Stage 5E Targeted Remediation (RC-C): COUNT companion to
-- RecommendedQtySkuIdsQuery.sql - identical WHERE clause (including the
-- same minStock/maxStock/minSales/maxSales computed-value filters), no
-- ORDER BY/LIMIT, and no ms_comm/ms_formula (never needed for a count).
SELECT COUNT(*)
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
