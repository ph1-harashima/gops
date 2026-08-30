-- COUNT companion to StockSalesListQuery.sql - same derived table, same WHERE.
SELECT COUNT(*) FROM (
${BASE_QUERY}
) stock_sales
WHERE (:minStock IS NULL OR stock_sales.current_stock >= :minStock)
  AND (:maxStock IS NULL OR stock_sales.current_stock <= :maxStock)
  AND (:minSales IS NULL OR stock_sales.monthly_sales >= :minSales)
  AND (:maxSales IS NULL OR stock_sales.monthly_sales <= :maxSales)
