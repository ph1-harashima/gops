-- Reduced Legacy Read Query for Order Candidate List + Recommended Qty (calc4 inputs).
--
-- Gulliver UI audit fix: :includeDeleted is a generic bypass of the
-- del_flg exclusion below - false for every "browse what's currently
-- orderable" caller (findOrderCandidates, the Stock/Sales List), true only
-- for findBySkus (re-fetching context for SKUs the caller already knows by
-- exact code, e.g. re-validating an existing Draft's lines - a soft-deleted
-- Item should still resolve there even though it no longer shows up in a
-- fresh browse list). Not an item-code-specific condition - any del_flg=1
-- Item behaves the same way.
--
-- Phase 8-H (Stock/Sales Visibility Foundation) addition: update_datetime
-- (agg.update_datetime, the SAME 'XX' aggregate ms_stk row every other
-- column here already comes from - additive column only, no JOIN/WHERE
-- change, reused as-is by StockSalesReadRepository's pagination wrapper).
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
-- Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
-- gops-stage4-targeted-real-data-remediation.md), replacing the prior
-- WH_CD='01' single-row Demo simplification:
--   - current_stock is now Legacy's own PHISICAL_QTY: SUM(ms_stk.stk_qty)
--     WHERE wh_cd IN ('4','5','6','7','8','10','11','12','15') - the exact
--     9-warehouse set MsStkRepositoryImpl.getBaseStockList() sums (WH1..WH5,
--     WH7..WH9,WH12 in Legacy's own aliasing), confirmed via Legacy source
--     (docs/real-data-audit/gops-stage3b-legacy-stock-logic-audit.md §3-4)
--     cross-verified against the real Warehouse Master (ms_comm
--     CATE_ID='MS_WH'): wh_cd='9' (不良品F/Defective), '13'
--     (個人オークション/Private Auction), '14' (廃棄倉庫/Disposal) are
--     excluded - matching Legacy's own commented-out JOIN lines and inline
--     "EXCLUDE DISPOSAL INVENTORY" comment exactly. A correlated subquery
--     (not 9 LEFT JOINs) keeps this query's existing "one row per item"
--     shape - no GROUP BY, no row-multiplication risk (Stage 3 Audit B5).
--   - This value is used both for the current_stock DISPLAY figure and, in
--     OverseasRecommendedQtyStrategy, as the PHISICAL_QTY component of
--     Legacy's own LOGICAL_QTY (= PHISICAL_QTY + open_po + open_ship,
--     confirmed Stage 3B §5 - ARR_QTY is deliberately never part of
--     LOGICAL_QTY, matching Legacy's own SQL, which sums PO_QTY_1..20 and
--     SHIP_QTY_1..10 only alongside the same 9-warehouse STK_QTY sum).
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
    COALESCE((
        SELECT SUM(phys.stk_qty) FROM ms_stk phys
        WHERE phys.item_cd = i.item_cd
          AND phys.wh_cd IN ('4','5','6','7','8','10','11','12','15')
    ), 0)                                                                        AS current_stock,
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
    latest_po.ccy         AS currency,
    agg.update_datetime   AS update_datetime
FROM ms_item i
LEFT JOIN ms_stk agg
       ON agg.item_cd = i.item_cd AND agg.wh_cd = 'XX'
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
WHERE (:includeDeleted = TRUE OR i.del_flg IS NULL OR i.del_flg = 0)
  AND (:brandCode IS NULL OR i.brand_cd = :brandCode)
  AND (:supplierCode IS NULL OR latest_po.supplier_cd = :supplierCode)
  AND (:keyword IS NULL OR i.item_cd LIKE :keywordLike OR i.description LIKE :keywordLike)
ORDER BY i.brand_cd, i.item_cd
