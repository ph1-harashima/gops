-- Stage 5E Targeted Remediation (RC-A, docs/real-data-audit/
-- gops-stage5e-targeted-remediation.md): Dashboard-only lean variant of
-- RecommendedQtyReadQuery.sql, used ONLY to compute candidateCount
-- (recommendedQty > 0 per item, which genuinely requires the full calc4
-- formula evaluation - Stage 5D classified this "Category B", not
-- reducible to a SQL aggregate the way outOfStockCount/
-- longTermOutOfStockCount are, see DashboardStockAggregateQuery.sql).
--
-- Deliberately omits BOTH ms_comm joins (brand_name AND supplier_name) -
-- Stage 5D's EXPLAIN ANALYZE identified these as a significant per-row
-- cost (looped once per outer row, not resolved via a single indexed
-- lookup). Dashboard needs brand_name for display (DashboardService's own
-- buildBrandRows), but resolves it via ONE separate, small bulk query
-- (~1,500 rows, CATE_ID='MS_BRAND' only) held in memory instead - the
-- same "load once, resolve in Java" pattern this Stage already applies to
-- Supplier Region Classification (RC-A's other confirmed N+1). Does not
-- need supplier_name at all for KPI computation - only supplier_cd (from
-- latest_po), to resolve region classification.
--
-- Every WH_CD/formula/join semantic below is otherwise byte-for-byte
-- identical to RecommendedQtyReadQuery.sql - this file changes column
-- selection and removes 2 JOINs only, never any calculation logic.
SELECT
    i.item_cd,
    i.brand_cd,
    COALESCE((
        SELECT SUM(phys.stk_qty) FROM ms_stk phys
        WHERE phys.item_cd = i.item_cd
          AND phys.wh_cd IN ('4','5','6','7','8','10','11','12','15')
    ), 0)                                                                        AS current_stock,
    COALESCE(agg.stk_standard, 0)                                               AS stk_standard,
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
    latest_po.supplier_cd
FROM ms_item i
LEFT JOIN ms_stk agg
       ON agg.item_cd = i.item_cd AND agg.wh_cd = 'XX'
LEFT JOIN ms_formula f
       ON f.id = i.item_cd
LEFT JOIN (
    SELECT d.item_cd, p.supplier_cd,
           ROW_NUMBER() OVER (PARTITION BY d.item_cd ORDER BY p.ordr_date DESC) AS rn
    FROM tr_po_dtl d
    JOIN tr_po p ON p.po_no = d.po_no
) latest_po
       ON latest_po.item_cd = i.item_cd AND latest_po.rn = 1
WHERE (i.del_flg IS NULL OR i.del_flg = 0)
