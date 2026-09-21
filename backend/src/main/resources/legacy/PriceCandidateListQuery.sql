-- Price Change candidate search - Backend Pagination (Stage 4 Targeted
-- Real-Data Remediation, docs/real-data-audit/
-- gops-stage4-targeted-real-data-remediation.md Remediation D). Wraps
-- PriceReadQuery.sql (loaded and substituted into the placeholder below by
-- LegacyPriceReadRepository's constructor) as a derived table - the SAME
-- search/filter logic this Foundation already established, not a
-- re-derived one. Mirrors StockSalesListQuery.sql's own base-query-reuse +
-- LIMIT/OFFSET pattern exactly, for one shared pagination contract across
-- both Legacy candidate-browse screens.
SELECT * FROM (
${BASE_QUERY}
) price_candidates
ORDER BY price_candidates.brand_cd, price_candidates.item_cd
LIMIT :limit OFFSET :offset
