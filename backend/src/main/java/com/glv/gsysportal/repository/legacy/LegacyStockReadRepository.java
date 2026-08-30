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
    private static final String STOCK_SALES_LIST_QUERY_RESOURCE = "legacy/StockSalesListQuery.sql";
    private static final String STOCK_SALES_LIST_COUNT_QUERY_RESOURCE = "legacy/StockSalesListCountQuery.sql";
    private static final String BASE_QUERY_PLACEHOLDER = "${BASE_QUERY}";

    private final NamedParameterJdbcTemplate legacyJdbc;
    private final String sql;
    private final String poHistorySql;
    private final String stockSalesListSql;
    private final String stockSalesListCountSql;

    public LegacyStockReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
        this.sql = loadSql(QUERY_RESOURCE);
        this.poHistorySql = loadSql(PO_HISTORY_QUERY_RESOURCE);
        // Phase 8-H: both compose the SAME loaded base query text (this.sql,
        // unchanged) as a derived table - never a second, independently
        // maintained copy of the JOIN/exclusion logic (8章).
        this.stockSalesListSql = loadSql(STOCK_SALES_LIST_QUERY_RESOURCE).replace(BASE_QUERY_PLACEHOLDER, this.sql);
        this.stockSalesListCountSql = loadSql(STOCK_SALES_LIST_COUNT_QUERY_RESOURCE).replace(BASE_QUERY_PLACEHOLDER, this.sql);
    }

    /**
     * Used by Create Draft (implementation instructions 4章): the Frontend
     * sends only SKU identifiers, and the Backend re-fetches the current
     * Legacy candidate context for exactly those SKUs here, rather than
     * trusting any Recommended Qty/Stock/Sales/Price value the Frontend may
     * have sent. Reuses {@link #findOrderCandidates} (no filter) and filters
     * in Java rather than adding a second, slightly-different SQL surface -
     * acceptable for this Demo Instance's small row count (Technical Design 17章).
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyStockRow> findBySkus(java.util.Collection<String> skus) {
        java.util.Set<String> requested = new java.util.HashSet<>(skus);
        return findOrderCandidates(null, null, null).stream()
                .filter(row -> requested.contains(row.itemCd()))
                .toList();
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyStockRow> findOrderCandidates(String brandCode, String supplierCode, String keyword) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("brandCode", brandCode)
                .addValue("supplierCode", supplierCode)
                .addValue("keyword", keyword)
                .addValue("keywordLike", keyword == null ? null : "%" + keyword + "%");

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
     * Stock/Sales List (Phase 8-H 4章/6章) - Backend-paginated, reusing
     * {@link #findOrderCandidates}'s exact query (see constructor) plus 2
     * pure-numeric range filters that query does not support. SKU/Item
     * keyword and Brand/Supplier filtering go through unchanged as the
     * SAME :keyword/:brandCode/:supplierCode params {@link #findOrderCandidates}
     * itself uses - not a second filtering mechanism.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyStockRow> findStockSalesList(StockSalesListFilter filter, int limit, int offset) {
        return legacyJdbc.query(stockSalesListSql, toStockSalesParams(filter).addValue("limit", limit).addValue("offset", offset),
                (rs, rowNum) -> new LegacyStockRow(
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
                        rs.getObject("update_datetime", java.time.LocalDateTime.class)));
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public long countStockSalesList(StockSalesListFilter filter) {
        Long count = legacyJdbc.queryForObject(stockSalesListCountSql, toStockSalesParams(filter), Long.class);
        return count == null ? 0L : count;
    }

    private static MapSqlParameterSource toStockSalesParams(StockSalesListFilter f) {
        return new MapSqlParameterSource()
                .addValue("brandCode", f.brandCode())
                .addValue("supplierCode", f.supplierCode())
                .addValue("keyword", f.skuKeyword())
                .addValue("keywordLike", f.skuKeyword() == null ? null : "%" + f.skuKeyword() + "%")
                .addValue("minStock", f.minStock())
                .addValue("maxStock", f.maxStock())
                .addValue("minSales", f.minSales())
                .addValue("maxSales", f.maxSales());
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
