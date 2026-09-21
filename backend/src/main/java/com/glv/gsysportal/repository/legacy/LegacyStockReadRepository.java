package com.glv.gsysportal.repository.legacy;

import com.glv.gsysportal.repository.legacy.row.LegacyPoHistoryRow;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;

/**
 * Legacy G-SYS Adapter - READ ONLY.
 *
 * Every method here is {@code @Transactional(readOnly = true, transactionManager
 * = "legacyTransactionManager")} - layer 3 of the READ ONLY guarantee (Technical
 * Design 4.1). This class issues SELECT-only SQL against the Legacy (Demo
 * Instance) MySQL via {@link NamedParameterJdbcTemplate}; it never uses JPA
 * entities or performs INSERT/UPDATE/DELETE against Legacy.
 */
@Repository
public class LegacyStockReadRepository {

    private static final String QUERY_RESOURCE = "legacy/RecommendedQtyReadQuery.sql";
    private static final String PO_HISTORY_QUERY_RESOURCE = "legacy/SkuPoHistoryReadQuery.sql";
    // Stage 5E Targeted Remediation (RC-C, docs/real-data-audit/
    // gops-stage5e-targeted-remediation.md): replaces the previous
    // StockSalesListQuery.sql/StockSalesListCountQuery.sql wrap-the-whole-
    // base-query-then-LIMIT approach (confirmed via Stage 5D's EXPLAIN
    // ANALYZE to materialize every Brand-matching row's full detail - brand/
    // supplier names, formula data - before the outer LIMIT trimmed it to
    // one page) with a two-step design: Step 1 (this file) resolves only
    // the page's item_cd values, cheaply; Step 2 reuses
    // RecommendedQtyReadQuery.sql's own new item_cd-set filter (see that
    // file's own Stage 5E comment) to fetch full detail for just that small,
    // already-paginated set.
    private static final String SKU_IDS_QUERY_RESOURCE = "legacy/RecommendedQtySkuIdsQuery.sql";
    private static final String SKU_IDS_COUNT_QUERY_RESOURCE = "legacy/RecommendedQtySkuIdsCountQuery.sql";
    // Stage 5E Targeted Remediation (RC-A): Dashboard-only queries - see
    // each file's own header comment.
    private static final String DASHBOARD_STOCK_AGGREGATE_QUERY_RESOURCE = "legacy/DashboardStockAggregateQuery.sql";
    private static final String DASHBOARD_CANDIDATE_INPUTS_QUERY_RESOURCE = "legacy/DashboardCandidateInputsQuery.sql";

    private final NamedParameterJdbcTemplate legacyJdbc;
    private final String sql;
    private final String poHistorySql;
    private final String skuIdsSql;
    private final String skuIdsCountSql;
    private final String dashboardStockAggregateSql;
    private final String dashboardCandidateInputsSql;

    public LegacyStockReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
        this.sql = loadSql(QUERY_RESOURCE);
        this.poHistorySql = loadSql(PO_HISTORY_QUERY_RESOURCE);
        this.skuIdsSql = loadSql(SKU_IDS_QUERY_RESOURCE);
        this.skuIdsCountSql = loadSql(SKU_IDS_COUNT_QUERY_RESOURCE);
        this.dashboardStockAggregateSql = loadSql(DASHBOARD_STOCK_AGGREGATE_QUERY_RESOURCE);
        this.dashboardCandidateInputsSql = loadSql(DASHBOARD_CANDIDATE_INPUTS_QUERY_RESOURCE);
    }

    /**
     * Used by Create Draft (implementation instructions 4章): the Frontend
     * sends only SKU identifiers, and the Backend re-fetches the current
     * Legacy candidate context for exactly those SKUs here, rather than
     * trusting any Recommended Qty/Stock/Sales/Price value the Frontend may
     * have sent. includeDeleted=true (Gulliver UI audit fix): a Draft may
     * already reference a since-soft-deleted Item (or is being re-validated
     * by exact code, not browsed) - resolving it here is a different
     * operation from {@link #findOrderCandidates}'s own "what's currently
     * orderable" browse list, which stays exclusion-filtered.
     *
     * <p>Stage 5E Targeted Remediation (RC-C, docs/real-data-audit/
     * gops-stage5e-targeted-remediation.md): now filters by the requested
     * {@code skus} in SQL (via {@link #queryCandidates}'s item_cd-set
     * parameter), not by fetching the entire catalog and filtering in Java.
     * Stage 5D's RCA found this method's old {@code queryCandidates(null,
     * null, null, true)} call - no brand/supplier/keyword filter at all -
     * was the primary, confirmed cause of SKU Detail's real-Production-scale
     * slowness: every call computed current_stock/formula/brand-name/
     * supplier-name for the ENTIRE catalog just to keep 1-2 rows.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyStockRow> findBySkus(Collection<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return List.of();
        }
        return queryCandidates(null, null, null, true, skus);
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyStockRow> findOrderCandidates(String brandCode, String supplierCode, String keyword) {
        return queryCandidates(brandCode, supplierCode, keyword, false, null);
    }

    private List<LegacyStockRow> queryCandidates(String brandCode, String supplierCode, String keyword,
                                                   boolean includeDeleted, Collection<String> itemCodes) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("brandCode", brandCode)
                .addValue("supplierCode", supplierCode)
                .addValue("keyword", keyword)
                .addValue("keywordLike", keyword == null ? null : "%" + keyword + "%")
                .addValue("includeDeleted", includeDeleted);
        bindItemCodes(params, itemCodes);

        return legacyJdbc.query(sql, params, (rs, rowNum) -> new LegacyStockRow(
                rs.getString("item_cd"),
                rs.getString("item_name"),
                rs.getString("brand_cd"),
                rs.getString("brand_name"),
                rs.getString("lead_time"),
                rs.getString("item_status"),
                rs.getObject("discon") == null ? null : rs.getBoolean("discon"),
                nullableInt(rs, "current_stock"),
                nullableInt(rs, "stk_standard"),
                nullableInt(rs, "monthly_sales"),
                nullableInt(rs, "open_po"),
                nullableInt(rs, "open_arrival"),
                nullableInt(rs, "open_ship"),
                rs.getString("formula_11"),
                rs.getString("formula_12"),
                rs.getString("formula_13"),
                rs.getString("formula_14"),
                rs.getString("supplier_cd"),
                rs.getString("supplier_name"),
                rs.getBigDecimal("unit_price"),
                rs.getString("currency"),
                rs.getObject("update_datetime", java.time.LocalDateTime.class)
        ));
    }

    /**
     * Stock/Sales List (Phase 8-H 4章/6章), and Candidate List's own paginated
     * path (via {@code OrderCandidateService.findOrderCandidatesPage}) -
     * Backend-paginated, same filters {@link #findOrderCandidates} itself
     * uses plus 2 pure-numeric range filters (minStock/maxStock/minSales/
     * maxSales) that query does not support.
     *
     * <p>Stage 5E Targeted Remediation (RC-C): two-step design, replacing
     * the previous single-query "wrap the whole base query as a derived
     * table, then LIMIT" approach. Stage 5D's EXPLAIN ANALYZE (against the
     * real Production Snapshot) confirmed that approach fully computed
     * every Brand-matching row's brand/supplier NAME and formula data
     * before the outer LIMIT ever trimmed it to one page - cost scaled with
     * total Brand size, not page size. Step 1
     * ({@code RecommendedQtySkuIdsQuery.sql}) resolves only the page's
     * item_cd values (current_stock/monthly_sales - both confirmed cheap,
     * well-indexed - are the only "expensive-looking" values it touches, and
     * only because minStock/maxStock/minSales/maxSales filter on them; no
     * ms_comm, no ms_formula). Step 2 reuses {@link #queryCandidates}'s
     * item_cd-set filter (same one {@link #findBySkus} uses) to fetch full
     * display detail for just that small, already-paginated set. Both steps
     * ORDER BY the same (brand_cd, item_cd), so Step 2's result order
     * matches Step 1's page order with no extra re-sorting needed.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyStockRow> findStockSalesList(StockSalesListFilter filter, int limit, int offset) {
        List<String> pageItemCodes = legacyJdbc.query(skuIdsSql,
                toSkuIdsParams(filter).addValue("limit", limit).addValue("offset", offset),
                (rs, rowNum) -> rs.getString("item_cd"));
        if (pageItemCodes.isEmpty()) {
            return List.of();
        }
        return queryCandidates(null, null, null, false, pageItemCodes);
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public long countStockSalesList(StockSalesListFilter filter) {
        Long count = legacyJdbc.queryForObject(skuIdsCountSql, toSkuIdsParams(filter), Long.class);
        return count == null ? 0L : count;
    }

    private static MapSqlParameterSource toSkuIdsParams(StockSalesListFilter f) {
        return new MapSqlParameterSource()
                .addValue("brandCode", f.brandCode())
                .addValue("supplierCode", f.supplierCode())
                .addValue("keyword", f.skuKeyword())
                .addValue("keywordLike", f.skuKeyword() == null ? null : "%" + f.skuKeyword() + "%")
                .addValue("minStock", f.minStock())
                .addValue("maxStock", f.maxStock())
                .addValue("minSales", f.minSales())
                .addValue("maxSales", f.maxSales())
                // Stock/Sales List is a browse view (same "what's currently
                // orderable" semantics as findOrderCandidates), so it stays
                // exclusion-filtered too - see RecommendedQtyReadQuery.sql's
                // own comment for the includeDeleted=true case (findBySkus only).
                .addValue("includeDeleted", false);
    }

    /**
     * Stage 5E Targeted Remediation (RC-A): 欠品/長期欠品 counts, overall and
     * per-Brand, computed as a genuine SQL aggregate (see
     * DashboardStockAggregateQuery.sql's own header comment for why this is
     * safe - pure arithmetic on current_stock/open_po, no formula
     * evaluation needed) instead of fetching every row and counting in a
     * Java Stream.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<DashboardStockAggregateRow> findDashboardStockAggregateByBrand() {
        return legacyJdbc.query(dashboardStockAggregateSql, new MapSqlParameterSource(), (rs, rowNum) ->
                new DashboardStockAggregateRow(
                        rs.getString("brand_cd"),
                        rs.getInt("out_of_stock_count"),
                        rs.getInt("long_term_out_of_stock_count")));
    }

    /**
     * Stage 5E Targeted Remediation (RC-A): the calc4-input columns for
     * every active item, WITHOUT the ms_comm brand/supplier NAME joins
     * (see DashboardCandidateInputsQuery.sql's own header comment) - used
     * only to compute candidateCount (recommendedQty > 0), which genuinely
     * needs the full formula evaluation and cannot be a SQL aggregate.
     * Returns {@link LegacyStockRow} for direct reuse with
     * {@code RecommendedQtyCalculator} - every field this query's SQL does
     * not select (item name, brand/supplier name, lead time, item status,
     * discon, price/currency, update timestamp) is simply null, which is
     * safe here since none of those are read by calc4/region resolution.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyStockRow> findDashboardCandidateInputs() {
        return legacyJdbc.query(dashboardCandidateInputsSql, new MapSqlParameterSource(), (rs, rowNum) -> new LegacyStockRow(
                rs.getString("item_cd"),
                null, // item_name - not selected, not needed for calc4/region
                rs.getString("brand_cd"),
                null, // brand_name - resolved separately in bulk, see DashboardService
                null, // lead_time
                null, // item_status
                null, // discon
                nullableInt(rs, "current_stock"),
                nullableInt(rs, "stk_standard"),
                null, // monthly_sales - not needed for calc4
                nullableInt(rs, "open_po"),
                nullableInt(rs, "open_arrival"),
                nullableInt(rs, "open_ship"),
                rs.getString("formula_11"),
                rs.getString("formula_12"),
                rs.getString("formula_13"),
                rs.getString("formula_14"),
                rs.getString("supplier_cd"),
                null, // supplier_name - not needed for candidateCount
                null, // unit_price
                null, // currency
                null  // update_datetime
        ));
    }

    /** Stage 5E Targeted Remediation (RC-A): one Brand's 欠品/長期欠品 counts
     * (see {@link #findDashboardStockAggregateByBrand}). */
    public record DashboardStockAggregateRow(String brandCd, int outOfStockCount, int longTermOutOfStockCount) {
    }

    /**
     * Stage 5E Targeted Remediation (RC-A): the entire Brand code->name
     * Master (CATE_ID='MS_BRAND', ~1,500 real rows) in one query, so
     * DashboardService can resolve every Brand's display name from an
     * in-memory Map instead of the per-row ms_comm JOIN
     * DashboardCandidateInputsQuery.sql deliberately omits.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public java.util.Map<String, String> findAllBrandNames() {
        List<java.util.Map.Entry<String, String>> rows = legacyJdbc.query(
                "SELECT code_id, code_name FROM ms_comm WHERE cate_id = 'MS_BRAND'",
                new MapSqlParameterSource(),
                (rs, rowNum) -> java.util.Map.entry(rs.getString("code_id"), rs.getString("code_name")));
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (var entry : rows) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** Phase 8-H 6章's minimum candidate Filter list. {@code skuKeyword}
     * matches SKU or Item Name (reuses RecommendedQtyReadQuery.sql's own
     * item_cd/description LIKE, see {@link #findOrderCandidates}).
     * {@code minStock}/{@code maxStock}/{@code minSales}/{@code maxSales}
     * are plain numeric ranges - not a Stock/Sales Business Rule (10章's
     * explicit instruction not to widen 欠品/長期欠品 scope). */
    public record StockSalesListFilter(String skuKeyword, String brandCode, String supplierCode,
                                        Integer minStock, Integer maxStock, Integer minSales, Integer maxSales) {
    }

    /**
     * Stage 5E Targeted Remediation (RC-C): binds the item_cd-set filter
     * pair ({@code :hasItemCodes}/{@code :itemCodes}, see
     * RecommendedQtyReadQuery.sql's own Stage 5E comment) safely - Spring's
     * NamedParameterJdbcTemplate expands {@code IN (:itemCodes)} from the
     * bound value's own type and cannot do that for a bare {@code null}, so
     * {@code :itemCodes} is always bound to a real, non-empty collection;
     * when the filter is inactive that collection is a single placeholder
     * value that can never match a real item_cd, and
     * {@code :hasItemCodes = false} makes the SQL's own {@code OR} short-
     * circuit before the placeholder value matters at all.
     */
    private static void bindItemCodes(MapSqlParameterSource params, Collection<String> itemCodes) {
        if (itemCodes == null || itemCodes.isEmpty()) {
            params.addValue("hasItemCodes", false);
            params.addValue("itemCodes", List.of("(none)"));
        } else {
            params.addValue("hasItemCodes", true);
            params.addValue("itemCodes", itemCodes);
        }
    }

    /**
     * MySQL's JDBC driver returns computed/aggregated integer expressions
     * (e.g. long chains of COALESCE(...)+COALESCE(...)) as java.lang.Long in
     * some cases even though the underlying columns are INT, which breaks a
     * naive {@code (Integer) rs.getObject(col)} cast. Widening via Number
     * avoids that without changing any query semantics.
     */
    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        return ((Number) value).intValue();
    }

    /**
     * SKU Detail's PO History (implementation instructions Step 5 4章) -
     * flat list of actual TR_PO/TR_PO_DTL rows for one SKU, ordered newest
     * first. No Sales Trend / date-window logic - just what Legacy has.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyPoHistoryRow> findPoHistoryBySku(String sku) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("sku", sku);
        return legacyJdbc.query(poHistorySql, params, (rs, rowNum) -> new LegacyPoHistoryRow(
                rs.getString("po_no"),
                rs.getObject("ordr_date", java.time.LocalDate.class),
                rs.getString("status"),
                nullableInt(rs, "qty_po"),
                rs.getBigDecimal("prc_unit"),
                rs.getString("ccy"),
                rs.getString("supplier_cd"),
                rs.getString("supplier_name")
        ));
    }

    private static String loadSql(String resourceName) {
        try {
            var resource = new ClassPathResource(resourceName);
            return new String(Files.readAllBytes(resource.getFile().toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Fallback for packaged (non-file-system) classpath resources, e.g. inside a jar.
            try (var is = new ClassPathResource(resourceName).getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException inner) {
                throw new UncheckedIOException("Failed to load " + resourceName, inner);
            }
        }
    }
}
