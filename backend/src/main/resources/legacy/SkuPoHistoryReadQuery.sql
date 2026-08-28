-- SKU Detail - Legacy PO History (implementation instructions Step 5 4章:
-- "取得可能なLegacyPO履歴のみ" - only what TR_PO/TR_PO_DTL actually contain,
-- never a fabricated/estimated history). READ ONLY, same Legacy Demo
-- Instance as RecommendedQtyReadQuery.sql. No Sales Trend / 30-60-90 day
-- windows are computed here - this is a flat list of past PO lines.

SELECT
    p.po_no,
    p.ordr_date,
    p.status,
    d.qty_po,
    d.prc_unit,
    p.ccy,
    p.supplier_cd,
    sup.code_name AS supplier_name
FROM tr_po_dtl d
JOIN tr_po p ON p.po_no = d.po_no
LEFT JOIN ms_comm sup ON sup.cate_id = 'MS_SUPPL' AND sup.code_id = p.supplier_cd
WHERE d.item_cd = :sku
ORDER BY p.ordr_date DESC, p.po_no DESC
