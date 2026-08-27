-- Reduced Legacy Read Query for Order Candidate List + Recommended Qty (calc4 inputs).
--
-- Extracted from (NOT copied verbatim from, per Technical Design 4.2 - "reduce columns,
-- keep JOIN/WHERE/exclusion rules unchanged"):
--   phasep-gulliver/gulliver/src/main/java/jp/ne/glv/repository/impl/MsStkRepositoryImpl.java
--   getBaseStockList()   (approx. lines 1560-2712)
--   getStockListOrder()  (approx. lines 3642-4737)
--
-- UNCHANGED from Legacy (per audit findings, must not be altered):
--   - MS_FORMULA join condition: LEFT JOIN MS_FORMULA ON MS_FORMULA.ID = MS_ITEM.ITEM_CD
--   - MS_STK primary/aggregate row convention: WH_CD = 'XX' holds STK_STANDARD, SOLD_QTY,
--     PO_QTY_*, ARR_QTY_*, SHIP_QTY_* (see Const.MS_STK_WH_CD_PRI in Legacy, confirmed via
--     SlTempostarImportBatch.java in Phase 0.5 audit)
--   - MS_COMM brand lookup: CATE_ID = 'MS_BRAND'
--
-- REDUCED vs. Legacy (deliberate, documented simplifications for this Demo Instance,
-- NOT a change to Legacy's calculation rules):
--   - Legacy's physical stock sum spans WH1..WH5,WH7..WH9,WH12 (excluding WH6=Damaged,
--     WH10=Private Auction, WH11=Disposal). This Demo Instance seeds only ONE physical
--     warehouse row (WH_CD='01') per item, so the physical-stock sum here is a
--     single-row LEFT JOIN rather than a 9-way LEFT JOIN. The exclusion RULE itself
--     (exclude Damaged/Private Auction/Disposal) is preserved conceptually; it simply
--     has no seeded rows to exclude in this reduced schema.
--   - The SHIP_QTY_* contribution to LOGICAL_QTY is included in the sum below for
--     completeness (Legacy's SQL adds it), but is 0 for all seeded items in this Step.
--   - An additional WH-quantity term that appeared in Legacy's raw LOGICAL_QTY SQL
--     (STK_QTY_4..15 summed a second time alongside PHISICAL_QTY) was flagged as
--     NOT FULLY RESOLVED in the Phase 0 Source Audit (its exact semantics were unclear).
--     It is deliberately NOT reproduced here rather than guessed at - see final
--     implementation report for this Step.
--   - Supplier is derived from the most recent TR_PO/TR_PO_DTL for the item (MS_ITEM
--     has NO supplier_cd column in Legacy at all - confirmed Phase 0 audit F章 - so
--     there is no "correct" master-data join to reduce from; this derivation is a
--     new, documented design decision for the Demo/Prototype, not a Legacy behavior).

SELECT
    i.item_cd,
    i.description        AS item_name,
    i.brand_cd,
    b.code_name           AS brand_name,
    i.lead_time,
    i.item_status,
    i.discon,
    COALESCE(phys.stk_qty, 0)                                                   AS current_stock,
    COALESCE(agg.stk_standard, 0)                                               AS stk_standard,
    COALESCE(agg.sold_qty, 0)                                                   AS monthly_sales,
    COALESCE(agg.po_qty_1,0)+COALESCE(agg.po_qty_2,0)+COALESCE(agg.po_qty_3,0)+COALESCE(agg.po_qty_4,0)+COALESCE(agg.po_qty_5,0)
      +COALESCE(agg.po_qty_6,0)+COALESCE(agg.po_qty_7,0)+COALESCE(agg.po_qty_8,0)+COALESCE(agg.po_qty_9,0)+COALESCE(agg.po_qty_10,0)
      +COALESCE(agg.po_qty_11,0)+COALESCE(agg.po_qty_12,0)+COALESCE(agg.po_qty_13,0)+COALESCE(agg.po_qty_14,0)+COALESCE(agg.po_qty_15,0)
      +COALESCE(agg.po_qty_16,0)+COALESCE(agg.po_qty_17,0)+COALESCE(agg.po_qty_18,0)+COALESCE(agg.po_qty_19,0)+COALESCE(agg.po_qty_20,0)
                                                                                 AS open_po,
    COALESCE(agg.arr_qty_1,0)+COALESCE(agg.arr_qty_2,0)+COALESCE(agg.arr_qty_3,0)+COALESCE(agg.arr_qty_4,0)+COALESCE(agg.arr_qty_5,0)
      +COALESCE(agg.arr_qty_6,0)+COALESCE(agg.arr_qty_7,0)+COALESCE(agg.arr_qty_8,0)+COALESCE(agg.arr_qty_9,0)+COALESCE(agg.arr_qty_10,0)
                                                                                 AS open_arrival,
    COALESCE(agg.ship_qty_1,0)+COALESCE(agg.ship_qty_2,0)+COALESCE(agg.ship_qty_3,0)+COALESCE(agg.ship_qty_4,0)+COALESCE(agg.ship_qty_5,0)
      +COALESCE(agg.ship_qty_6,0)+COALESCE(agg.ship_qty_7,0)+COALESCE(agg.ship_qty_8,0)+COALESCE(agg.ship_qty_9,0)+COALESCE(agg.ship_qty_10,0)
                                                                                 AS open_ship,
    f.formula_11,
    f.formula_12,
    f.formula_13,
    f.formula_14,
    latest_po.supplier_cd,
    sup.code_name         AS supplier_name,
    latest_po.prc_unit    AS unit_price,
    latest_po.ccy         AS currency
FROM ms_item i
LEFT JOIN ms_stk agg
       ON agg.item_cd = i.item_cd AND agg.wh_cd = 'XX'
LEFT JOIN ms_stk phys
       ON phys.item_cd = i.item_cd AND phys.wh_cd = '01'
LEFT JOIN ms_formula f
       ON f.id = i.item_cd
LEFT JOIN ms_comm b
       ON b.cate_id = 'MS_BRAND' AND b.code_id = i.brand_cd
LEFT JOIN (
    SELECT d.item_cd, p.supplier_cd, d.prc_unit, p.ccy,
           ROW_NUMBER() OVER (PARTITION BY d.item_cd ORDER BY p.ordr_date DESC) AS rn
    FROM tr_po_dtl d
    JOIN tr_po p ON p.po_no = d.po_no
) latest_po
       ON latest_po.item_cd = i.item_cd AND latest_po.rn = 1
LEFT JOIN ms_comm sup
       ON sup.cate_id = 'MS_SUPPL' AND sup.code_id = latest_po.supplier_cd
WHERE i.del_flg IS NULL OR i.del_flg = 0
  AND (:brandCode IS NULL OR i.brand_cd = :brandCode)
  AND (:supplierCode IS NULL OR latest_po.supplier_cd = :supplierCode)
  AND (:keyword IS NULL OR i.item_cd LIKE :keywordLike OR i.description LIKE :keywordLike)
ORDER BY i.brand_cd, i.item_cd
