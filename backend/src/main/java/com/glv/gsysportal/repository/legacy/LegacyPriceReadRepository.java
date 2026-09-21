package com.glv.gsysportal.repository.legacy;

import com.glv.gsysportal.repository.legacy.row.LegacyPriceRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Legacy G-SYS Adapter - READ ONLY (same 3-layer guarantee as
 * {@link LegacyStockReadRepository}/{@link LegacyPoConcurrencyReadRepository},
 * Technical Design 4.1). Phase 8-B (Price Change Foundation).
 *
 * <p><b>Why a new Repository rather than widening {@link LegacyStockReadRepository}</b>:
 * that Repository's row type ({@code LegacyStockRow}) and query already carry
 * stock/PO-history columns Price Change never uses, and adding 5 more columns
 * to every one of its callers (Order Candidate List, SKU Detail) for a
 * feature they don't need would be the same anti-pattern
 * {@link LegacyPoConcurrencyReadRepository}'s own Javadoc already rejected
 * once (7-C6 0章's evaluation). Same pattern-level reuse as always: identical
 * {@code NamedParameterJdbcTemplate}, {@code legacyTransactionManager}, and
 * {@code del_flg} soft-delete convention.
 *
 * <p>{@link #findBySkus} mirrors {@link LegacyStockReadRepository#findBySkus}
 * exactly (Backend re-fetches by identifier rather than trusting any
 * Frontend-sent price/margin value) - this is the method Baseline Snapshot
 * (target-price-change-workflow.md 7章) and Concurrency Check (13章) both
 * call: Baseline captures a copy of what this returns at Change Set Detail
 * creation time, Concurrency re-calls it and compares.
 */
@Repository
public class LegacyPriceReadRepository {

    private static final String QUERY_RESOURCE = "legacy/PriceReadQuery.sql";
    private static final String LIST_QUERY_RESOURCE = "legacy/PriceCandidateListQuery.sql";
    private static final String LIST_COUNT_QUERY_RESOURCE = "legacy/PriceCandidateListCountQuery.sql";
    private static final String BASE_QUERY_PLACEHOLDER = "${BASE_QUERY}";

    private final NamedParameterJdbcTemplate legacyJdbc;
    private final String sql;
    private final String listSql;
    private final String listCountSql;

    public LegacyPriceReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
        this.sql = loadSql(QUERY_RESOURCE);
        // Stage 4 Targeted Real-Data Remediation (Remediation D): same
        // base-query-reuse convention LegacyStockReadRepository's own
        // constructor already established for StockSalesListQuery.sql.
        this.listSql = loadSql(LIST_QUERY_RESOURCE).replace(BASE_QUERY_PLACEHOLDER, this.sql);
        this.listCountSql = loadSql(LIST_COUNT_QUERY_RESOURCE).replace(BASE_QUERY_PLACEHOLDER, this.sql);
    }

    /** Re-fetch by identifier - see class Javadoc. Used for Product Selection
     * candidate re-validation, Baseline Snapshot capture, and Concurrency
     * Check's "current Legacy value" side. */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyPriceRow> findBySkus(Collection<String> skus) {
        Set<String> requested = new HashSet<>(skus);
        return search(null, null, null).stream()
                .filter(row -> requested.contains(row.itemCd()))
                .toList();
    }

    /** Product Selection screen's search/filter (target-price-change-workflow.md
     * 15章). {@code itemGrpCode} selects every SKU belonging to one Item Group
     * in a single call - the "Item Groupを起点とした複数選択" bulk path
     * (9章), resolved server-side rather than the Frontend holding/posting a
     * large SKU array (9章's explicit large-SKU-count warning). */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyPriceRow> search(String brandCode, String itemGrpCode, String keyword) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("brandCode", brandCode)
                .addValue("itemGrpCode", itemGrpCode)
                .addValue("keyword", keyword)
                .addValue("keywordLike", keyword == null ? null : "%" + keyword + "%");

        return legacyJdbc.query(sql, params, (rs, rowNum) -> new LegacyPriceRow(
                rs.getString("item_cd"),
                rs.getString("item_name"),
                rs.getString("brand_cd"),
                rs.getString("brand_name"),
                rs.getString("item_grp_cd"),
                rs.getString("item_status"),
                nullableBoolean(rs, "discon"),
                rs.getBigDecimal("prc_sell_w_tax"),
                rs.getBigDecimal("cost_this_month_avg"),
                nullableBoolean(rs, "free_ship_flg"),
                rs.getBigDecimal("ship_fee")
        ));
    }

    /**
     * Stage 4 Targeted Real-Data Remediation (Remediation D,
     * docs/real-data-audit/gops-stage4-targeted-real-data-remediation.md):
     * Backend-paginated Product Selection search - a confirmed real Brand
     * has 18,596 SKUs (Stage 2 §5) and {@link #search} itself returns every
     * matching row unpaginated. {@link #search}/{@link #findBySkus} are
     * deliberately left unchanged - Baseline Snapshot/Concurrency Check
     * re-fetch by exact identifier and must keep working exactly as before.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyPriceRow> searchPage(String brandCode, String itemGrpCode, String keyword, int limit, int offset) {
        MapSqlParameterSource params = searchParams(brandCode, itemGrpCode, keyword)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return legacyJdbc.query(listSql, params, LegacyPriceReadRepository::mapRow);
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public long countSearch(String brandCode, String itemGrpCode, String keyword) {
        Long count = legacyJdbc.queryForObject(listCountSql, searchParams(brandCode, itemGrpCode, keyword), Long.class);
        return count == null ? 0L : count;
    }

    private static MapSqlParameterSource searchParams(String brandCode, String itemGrpCode, String keyword) {
        return new MapSqlParameterSource()
                .addValue("brandCode", brandCode)
                .addValue("itemGrpCode", itemGrpCode)
                .addValue("keyword", keyword)
                .addValue("keywordLike", keyword == null ? null : "%" + keyword + "%");
    }

    private static LegacyPriceRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new LegacyPriceRow(
                rs.getString("item_cd"),
                rs.getString("item_name"),
                rs.getString("brand_cd"),
                rs.getString("brand_name"),
                rs.getString("item_grp_cd"),
                rs.getString("item_status"),
                nullableBoolean(rs, "discon"),
                rs.getBigDecimal("prc_sell_w_tax"),
                rs.getBigDecimal("cost_this_month_avg"),
                nullableBoolean(rs, "free_ship_flg"),
                rs.getBigDecimal("ship_fee")
        );
    }

    /** Distinct, non-null Item Group codes across all non-deleted items - the
     * Item Group picker's option list for Bulk Selection (9章). No Item Group
     * "name"/label exists anywhere in Legacy (confirmed
     * docs/legacy-price-change-reverse-engineering.md 8章: {@code MsItemGrp}
     * has no name field) - the code itself is the only identifier, and the
     * only thing this can ever show. */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<String> findDistinctItemGroupCodes() {
        return legacyJdbc.queryForList(
                "SELECT DISTINCT item_grp_cd FROM ms_item "
                        + "WHERE (del_flg IS NULL OR del_flg = 0) AND item_grp_cd IS NOT NULL "
                        + "ORDER BY item_grp_cd",
                new MapSqlParameterSource(), String.class);
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column) == null ? null : rs.getBoolean(column);
    }

    private static String loadSql(String resourceName) {
        try {
            var resource = new ClassPathResource(resourceName);
            return new String(Files.readAllBytes(resource.getFile().toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            try (var is = new ClassPathResource(resourceName).getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException inner) {
                throw new UncheckedIOException("Failed to load " + resourceName, inner);
            }
        }
    }
}
