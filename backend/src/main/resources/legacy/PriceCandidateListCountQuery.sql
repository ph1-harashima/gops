-- COUNT companion to PriceCandidateListQuery.sql - same derived table, no
-- extra WHERE (PriceReadQuery.sql's own base WHERE already carries every
-- filter this search supports).
SELECT COUNT(*) FROM (
${BASE_QUERY}
) price_candidates
