-- Legacy Read Query for Price Change Foundation (Phase 8-B).
--
-- Column source: docs/legacy-price-change-reverse-engineering.md 5章
-- (jp.ne.glv.model.MsItem: ITEM_GRP_CD/PRC_SELL_W_TAX/COST_THIS_MONTH_AVG/
-- FREE_SHIP_FLG/SHIP_FEE), reduced to exactly the columns
-- com.glv.gsysportal.legacy.calc.MarginCalculator needs plus display fields
-- (SKU/Item Name/Item Group/Brand - target-price-change-workflow.md 3章's
-- minimal candidate list). MS_COMM brand lookup (CATE_ID='MS_BRAND') is the
-- same join condition already established in RecommendedQtyReadQuery.sql -
-- kept unchanged rather than re-derived.
--
-- List/Sale price, B2B/Korea price, and every EC-mall-specific price column
-- on the real MsItem are deliberately NOT selected here - out of Foundation
-- scope (see MarginCalculator's Javadoc and target-price-change-workflow.md
-- 8章, "Current Selling Price" only).

SELECT
    i.item_cd,
    i.description          AS item_name,
    i.brand_cd,
    b.code_name             AS brand_name,
    i.item_grp_cd,
    i.item_status,
    i.discon,
    i.prc_sell_w_tax,
    i.cost_this_month_avg,
    i.free_ship_flg,
    i.ship_fee
FROM ms_item i
LEFT JOIN ms_comm b
       ON b.cate_id = 'MS_BRAND' AND b.code_id = i.brand_cd
WHERE (i.del_flg IS NULL OR i.del_flg = 0)
  AND (:itemGrpCode IS NULL OR i.item_grp_cd = :itemGrpCode)
  AND (:brandCode IS NULL OR i.brand_cd = :brandCode)
  AND (:keyword IS NULL OR i.item_cd LIKE :keywordLike OR i.description LIKE :keywordLike)
ORDER BY i.brand_cd, i.item_cd
