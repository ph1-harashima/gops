-- Arrival List (Phase 8-G 5章/6章). Driving table is tr_arr itself (the
-- Arrival record) - Ordered/Invoice/Stock-In Qty are derived per-row via
-- correlated subqueries against tr_po_dtl/tr_inv_dtl, at the SAME
-- (supplier_cd, po_no, inv_no) header granularity tr_arr.qty already uses
-- (Phase 8-G 7章). Stock-In Qty nets Original + linked Credit invoice rows,
-- reusing the exact mechanism FulfillmentReadRepository established in
-- Phase 7-C7A (po_no = tr_arr.po_no OR po_no LIKE 'tr_arr.po_no-%').
--
-- Deliberately does NOT join ms_stk anywhere (Phase 8-G 11章's most
-- important constraint - Arrival and Warehouse Stock must never be joined).
SELECT
    a.supplier_cd,
    a.po_no,
    a.inv_no,
    a.brand_cd,
    b.code_name  AS brand_name,
    sup.code_name AS supplier_name,
    a.bl_no,
    a.vessel_no,
    a.qty        AS arrival_qty,
    a.etd,
    a.eta,
    a.eta_wh,
    a.stk_in_date,
    a.wh_rep_status,
    a.wh_rep_result,
    -- No COALESCE(...,0) on any of the 3 sums below: a genuine "no matching
    -- row" (SUM over zero rows) and a genuine "row(s) exist but qty_stk_in
    -- is still NULL in Source" (e.g. not yet Stock-In-Reported) must both
    -- surface as NULL to the Frontend, never fabricated as 0 (Phase 8-G 7章
    -- - see ArrivalServiceIntegrationTest.inTransitArrivalLeavesStockInQtyNull).
    (SELECT SUM(p.qty_po) FROM tr_po_dtl p
       WHERE p.po_no = a.po_no AND (p.del_flg IS NULL OR p.del_flg = 0)) AS ordered_qty,
    (SELECT SUM(d.qty) FROM tr_inv_dtl d
       WHERE d.supplier_cd = a.supplier_cd AND d.inv_no = a.inv_no
         AND (d.del_flg IS NULL OR d.del_flg = 0)) AS invoice_qty,
    (SELECT SUM(d.qty_stk_in) FROM tr_inv_dtl d
       WHERE d.supplier_cd = a.supplier_cd
         AND (d.po_no = a.po_no OR d.po_no LIKE CONCAT(a.po_no, '-%'))
         AND (d.del_flg IS NULL OR d.del_flg = 0)) AS stock_in_qty
FROM tr_arr a
LEFT JOIN ms_comm b   ON b.cate_id = 'MS_BRAND' AND b.code_id = a.brand_cd
LEFT JOIN ms_comm sup ON sup.cate_id = 'MS_SUPPL' AND sup.code_id = a.supplier_cd
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
ORDER BY a.eta DESC, a.po_no, a.inv_no
LIMIT :limit OFFSET :offset
