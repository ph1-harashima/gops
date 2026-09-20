-- Post-Freeze Business Refinement (docs/gops-20260917-business-requirements-re-audit.md
-- §6/§8, Type A "Legacy Expected Arrival"): per-SKU lookup of the nearest
-- still-open (not yet Stock-In'd) Arrival's Expected Arrival Date, driven
-- from tr_po_dtl (which carries item_cd) joined to tr_arr on po_no - the
-- SAME join shape ArrivalListQuery.sql's own skuKeyword filter already
-- uses, just inverted into a per-SKU aggregate instead of a header list.
-- Deliberately does NOT join ms_stk (same Phase 8-G 11章 constraint every
-- other query in this package respects).
--
-- eta_wh (arrival AT THE WAREHOUSE - the practically "is this sellable yet"
-- date) is preferred over eta (arrival at port only); eta is the fallback
-- when eta_wh is not yet known. Only rows with stk_in_date IS NULL count -
-- once actually stocked in, this is no longer a pending "expected" date for
-- Portal Stockout-date purposes (the SKU would no longer even be flagged
-- OOS by then, since ms_stk would reflect the new stock).
SELECT
    p.item_cd,
    MIN(COALESCE(a.eta_wh, a.eta)) AS expected_arrival_date
FROM tr_po_dtl p
JOIN tr_arr a ON a.po_no = p.po_no
WHERE (p.del_flg IS NULL OR p.del_flg = 0)
  AND (a.del_flg IS NULL OR a.del_flg = 0)
  AND a.stk_in_date IS NULL
  AND COALESCE(a.eta_wh, a.eta) IS NOT NULL
  AND p.item_cd IN (:skuCodes)
GROUP BY p.item_cd
