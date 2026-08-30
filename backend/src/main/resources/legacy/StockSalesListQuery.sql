-- Stock / Sales List (Phase 8-H 4章/6章). Wraps RecommendedQtyReadQuery.sql
-- (loaded and substituted into the placeholder below by
-- LegacyStockReadRepository's constructor) as a derived table - the SAME
-- calculation/JOIN/exclusion logic Order
-- Candidate List and SKU Detail already use, not a re-derived one (Phase
-- 8-H 8章's explicit instruction). SKU/Item keyword and Brand/Supplier
-- filtering reuse that base query's own :keyword/:keywordLike/:brandCode/
-- :supplierCode params unchanged - only Backend Pagination and the 2 pure-
-- numeric range filters it does not support (minStock/maxStock/minSales/
-- maxSales, Phase 8-H 6章) are added here.
SELECT * FROM (
${BASE_QUERY}
) stock_sales
WHERE (:minStock IS NULL OR stock_sales.current_stock >= :minStock)
  AND (:maxStock IS NULL OR stock_sales.current_stock <= :maxStock)
  AND (:minSales IS NULL OR stock_sales.monthly_sales >= :minSales)
  AND (:maxSales IS NULL OR stock_sales.monthly_sales <= :maxSales)
ORDER BY stock_sales.brand_cd, stock_sales.item_cd
LIMIT :limit OFFSET :offset
