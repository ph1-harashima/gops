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

    private final NamedParameterJdbcTemplate legacyJdbc;
    private final String sql;
    private final String poHistorySql;

    public LegacyStockReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
        this.sql = loadSql(QUERY_RESOURCE);
        this.poHistorySql = loadSql(PO_HISTORY_QUERY_RESOURCE);
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
                rs.getString("currency")
        ));
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
