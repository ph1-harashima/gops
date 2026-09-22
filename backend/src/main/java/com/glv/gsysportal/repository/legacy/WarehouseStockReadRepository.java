package com.glv.gsysportal.repository.legacy;

import com.glv.gsysportal.repository.legacy.row.LegacyWarehouseStockRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Legacy G-SYS Adapter - READ ONLY (Phase 8-G, Warehouse Stock Visibility
 * Foundation). Backed entirely by {@code ms_stk} (excluding the {@code 'XX'}
 * aggregate row - see WarehouseStockListQuery.sql's own header comment).
 *
 * <p><b>Never joins {@code tr_arr}/{@code tr_po}/{@code tr_inv} anywhere</b>
 * - Phase 8-G 11章's most important constraint, the mirror image of
 * {@link ArrivalReadRepository}'s own restriction.
 *
 * <p>Warehouse Stock Production-Scale Remediation (docs/real-data-audit/
 * gops-warehouse-stock-production-scale-remediation.md): {@link #findList}
 * is now a two-step fetch (Stage 5E RC-C's own "resolve page keys cheaply,
 * fetch detail only for the page" pattern) instead of one query joining
 * full detail before {@code ORDER BY}/{@code LIMIT} - the RCA's own
 * finding (a {@code brand_cd, item_cd, wh_cd} sort with no covering index
 * across the {@code ms_item x ms_stk} join forced a filesort over the
 * full, unbounded joined result before any row reached {@code LIMIT}).
 * {@link #countList} is unchanged - measured separately (remediation doc
 * §5/§9) and confirmed not the bottleneck (its own {@code EXPLAIN} has no
 * {@code ORDER BY} and no filesort).
 */
@Repository
public class WarehouseStockReadRepository {

    private static final String LIST_COUNT_QUERY_RESOURCE = "legacy/WarehouseStockListCountQuery.sql";
    private static final String BY_SKU_QUERY_RESOURCE = "legacy/WarehouseStockBySkuQuery.sql";
    private static final String PAGE_KEYS_QUERY_RESOURCE = "legacy/WarehouseStockPageKeysQuery.sql";
    private static final String DETAIL_BY_ITEM_CODES_QUERY_RESOURCE = "legacy/WarehouseStockDetailByItemCodesQuery.sql";

    private final NamedParameterJdbcTemplate legacyJdbc;
    private final String listCountSql;
    private final String bySkuSql;
    private final String pageKeysSql;
    private final String detailByItemCodesSql;

    public WarehouseStockReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
        this.listCountSql = loadSql(LIST_COUNT_QUERY_RESOURCE);
        this.bySkuSql = loadSql(BY_SKU_QUERY_RESOURCE);
        this.pageKeysSql = loadSql(PAGE_KEYS_QUERY_RESOURCE);
        this.detailByItemCodesSql = loadSql(DETAIL_BY_ITEM_CODES_QUERY_RESOURCE);
    }

    /**
     * Step 1 ({@link #findPageKeys}) resolves this page's ordered
     * {@code (brand_cd, item_cd, wh_cd)} keys with the leanest possible
     * query (no item description, no brand-name join - both free-text/
     * wider columns that would otherwise inflate the filesort). Step 2
     * ({@link #findDetailByItemCodes}) fetches full display detail for
     * every physical-warehouse row of the (small) set of distinct
     * {@code item_cd} values Step 1 returned - deliberately a superset
     * (every warehouse row for each of those items, not just the exact
     * page's pairs), because an exact-pair SQL filter would need either
     * dynamic SQL or a multi-column {@code IN}, and the superset here is
     * already tightly bounded (one page's SKUs x a handful of real
     * warehouses each, never Production-scale). The exact page - correct
     * pairs only, in Step 1's own order - is reconstructed here in Java,
     * a cheap in-memory operation over at most a few hundred rows.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyWarehouseStockRow> findList(WarehouseStockListFilter filter, int limit, int offset) {
        List<WarehouseStockPageKey> pageKeys = findPageKeys(filter, limit, offset);
        if (pageKeys.isEmpty()) {
            return List.of();
        }
        Set<String> itemCodes = new LinkedHashSet<>();
        for (WarehouseStockPageKey key : pageKeys) {
            itemCodes.add(key.itemCd());
        }
        List<LegacyWarehouseStockRow> detailSuperset = findDetailByItemCodes(itemCodes);
        Map<String, LegacyWarehouseStockRow> byPairKey = new LinkedHashMap<>();
        for (LegacyWarehouseStockRow row : detailSuperset) {
            byPairKey.put(pairKey(row.itemCd(), row.whCd()), row);
        }

        List<LegacyWarehouseStockRow> page = new ArrayList<>(pageKeys.size());
        for (WarehouseStockPageKey key : pageKeys) {
            LegacyWarehouseStockRow row = byPairKey.get(pairKey(key.itemCd(), key.whCd()));
            // A Step 1/Step 2 mismatch (e.g. a genuinely concurrent Legacy
            // write between the two SELECTs) is skipped, not thrown - the
            // same "never surface a transient Legacy race as an
            // application error" posture every other Legacy read in this
            // codebase already takes; Step 1's own row already carries
            // everything the empty-result case needs to not be silently
            // wrong (it just would not happen against a static Snapshot).
            if (row != null) {
                page.add(row);
            }
        }
        return page;
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    List<WarehouseStockPageKey> findPageKeys(WarehouseStockListFilter filter, int limit, int offset) {
        return legacyJdbc.query(pageKeysSql, toParams(filter).addValue("limit", limit).addValue("offset", offset),
                (rs, rowNum) -> new WarehouseStockPageKey(
                        rs.getString("brand_cd"), rs.getString("item_cd"), rs.getString("wh_cd")));
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    List<LegacyWarehouseStockRow> findDetailByItemCodes(Set<String> itemCodes) {
        if (itemCodes.isEmpty()) {
            return List.of();
        }
        return legacyJdbc.query(detailByItemCodesSql,
                new MapSqlParameterSource("itemCodes", itemCodes), WarehouseStockReadRepository::toRow);
    }

    private static String pairKey(String itemCd, String whCd) {
        return itemCd + "\u0000" + whCd;
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public long countList(WarehouseStockListFilter filter) {
        Long count = legacyJdbc.queryForObject(listCountSql, toParams(filter), Long.class);
        return count == null ? 0L : count;
    }

    /** Warehouse Stock Detail (Phase 8-G 10章, Drawer/Expand form) - every
     * physical-warehouse row for one SKU, unpaginated (bounded by the number
     * of real warehouses, never large). */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyWarehouseStockRow> findByItemCd(String itemCd) {
        return legacyJdbc.query(bySkuSql, new MapSqlParameterSource("itemCd", itemCd),
                WarehouseStockReadRepository::toRow);
    }

    private static MapSqlParameterSource toParams(WarehouseStockListFilter f) {
        return new MapSqlParameterSource()
                .addValue("skuKeyword", f.skuKeyword())
                .addValue("skuKeywordLike", f.skuKeyword() == null ? null : "%" + f.skuKeyword() + "%")
                .addValue("brandCode", f.brandCode())
                .addValue("whCode", f.whCode())
                .addValue("minQty", f.minQty())
                .addValue("maxQty", f.maxQty());
    }

    private static LegacyWarehouseStockRow toRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Object stkQty = rs.getObject("stk_qty");
        return new LegacyWarehouseStockRow(
                rs.getString("wh_cd"),
                rs.getString("item_cd"),
                rs.getString("item_name"),
                rs.getString("brand_cd"),
                rs.getString("brand_name"),
                stkQty == null ? null : ((Number) stkQty).intValue(),
                rs.getObject("update_datetime", LocalDateTime.class));
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

    /** Warehouse Stock List's Filter (Phase 8-G 9章's minimum candidate
     * list). {@code minQty}/{@code maxQty} are a plain numeric range - not a
     * "stock available/out of stock" judgment (9章's explicit instruction
     * not to widen Business Rule scope). */
    public record WarehouseStockListFilter(String skuKeyword, String brandCode, String whCode, Integer minQty, Integer maxQty) {
    }

    /** One page row's ordering key ({@code brand_cd, item_cd, wh_cd}) -
     * Step 1's own, minimal result shape (§3 of the remediation doc: one
     * page row = one physical {@code (item_cd, wh_cd)} pair, never one
     * SKU). {@code brandCd} is carried through only because it is the
     * primary sort key and free to keep once selected - never re-queried. */
    record WarehouseStockPageKey(String brandCd, String itemCd, String whCd) {
    }
}
