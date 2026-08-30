-- COUNT companion to ArrivalListQuery.sql - same WHERE clause, no derived
-- Qty subqueries/JOINs needed since none of them are filtered on (Phase 8-G
-- 5章's Backend Pagination requirement).
SELECT COUNT(*)
FROM tr_arr a
WHERE (a.del_flg IS NULL OR a.del_flg = 0)
  AND (:supplierCode IS NULL OR a.supplier_cd = :supplierCode)
  AND (:brandCode IS NULL OR a.brand_cd = :brandCode)
  AND (:poNumber IS NULL OR a.po_no LIKE :poNumberLike)
  AND (:invoiceNumber IS NULL OR a.inv_no LIKE :invoiceNumberLike)
  AND (:skuKeyword IS NULL OR EXISTS (
        SELECT 1 FROM tr_inv_dtl d
        WHERE d.supplier_cd = a.supplier_cd AND d.inv_no = a.inv_no
          AND (d.item_cd LIKE :skuKeywordLike)
     ))
  AND (:arrivalDateFrom IS NULL OR a.eta >= :arrivalDateFrom)
  AND (:arrivalDateTo IS NULL OR a.eta <= :arrivalDateTo)
