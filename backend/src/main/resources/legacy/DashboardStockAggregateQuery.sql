-- Stage 5E Targeted Remediation (RC-A, docs/real-data-audit/
-- gops-stage5e-targeted-remediation.md): outOfStockCount/
-- longTermOutOfStockCount (both overall and per-Brand) are pure
-- arithmetic on current_stock/open_po - Stage 5D classified these
-- "Category A": computable as a genuine SQL aggregate, unlike
-- candidateCount (Category B, needs the full calc4 formula chain - see
-- DashboardCandidateInputsQuery.sql). Computed here as one GROUP BY
-- brand_cd query - the overall totals are summed from these per-Brand
-- rows in Java (trivial, no second query needed).
--
-- Same current_stock/open_po definitions as RecommendedQtyReadQuery.sql
-- (PHISICAL_QTY over WH_CD IN ('4','5','6','7','8','10','11','12','15'),
-- unchanged) and the SAME 欠品/長期欠品 proxy definitions
-- DashboardService itself already used (currentStock==0 /
-- currentStock==0 AND openPo==0) - this query changes only WHERE the
-- counting happens (SQL GROUP BY instead of a Java Stream over every
-- fully-materialized row), never what counts as "out of stock".
SELECT
    t.brand_cd,
    SUM(CASE WHEN t.current_stock = 0 THEN 1 ELSE 0 END)                                    AS out_of_stock_count,
    SUM(CASE WHEN t.current_stock = 0 AND t.open_po = 0 THEN 1 ELSE 0 END)                   AS long_term_out_of_stock_count
FROM (
    SELECT
        i.item_cd,
        i.brand_cd,
        COALESCE((
            SELECT SUM(phys.stk_qty) FROM ms_stk phys
            WHERE phys.item_cd = i.item_cd
              AND phys.wh_cd IN ('4','5','6','7','8','10','11','12','15')
        ), 0) AS current_stock,
        COALESCE(agg.po_qty_1,0)+COALESCE(agg.po_qty_2,0)+COALESCE(agg.po_qty_3,0)+COALESCE(agg.po_qty_4,0)+COALESCE(agg.po_qty_5,0)
          +COALESCE(agg.po_qty_6,0)+COALESCE(agg.po_qty_7,0)+COALESCE(agg.po_qty_8,0)+COALESCE(agg.po_qty_9,0)+COALESCE(agg.po_qty_10,0)
          +COALESCE(agg.po_qty_11,0)+COALESCE(agg.po_qty_12,0)+COALESCE(agg.po_qty_13,0)+COALESCE(agg.po_qty_14,0)+COALESCE(agg.po_qty_15,0)
          +COALESCE(agg.po_qty_16,0)+COALESCE(agg.po_qty_17,0)+COALESCE(agg.po_qty_18,0)+COALESCE(agg.po_qty_19,0)+COALESCE(agg.po_qty_20,0) AS open_po
    FROM ms_item i
    LEFT JOIN ms_stk agg
           ON agg.item_cd = i.item_cd AND agg.wh_cd = 'XX'
    WHERE (i.del_flg IS NULL OR i.del_flg = 0)
) t
GROUP BY t.brand_cd
