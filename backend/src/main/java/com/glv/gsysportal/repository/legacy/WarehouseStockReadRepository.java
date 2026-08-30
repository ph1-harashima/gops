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
import java.util.List;

/**
 * Legacy G-SYS Adapter - READ ONLY (Phase 8-G, Warehouse Stock Visibility
 * Foundation). Backed entirely by {@code ms_stk} (excluding the {@code 'XX'}
 * aggregate row - see WarehouseStockListQuery.sql's own header comment).
 *
 * <p><b>Never joins {@code tr_arr}/{@code tr_po}/{@code tr_inv} anywhere</b>
 * - Phase 8-G 11章's most important constraint, the mirror image of
 * {@link ArrivalReadRepository}'s own restriction.
 */
@Repository
public class WarehouseStockReadRepository {

    private static final String LIST_QUERY_RESOURCE = "legacy/WarehouseStockListQuery.sql";
    private static final String LIST_COUNT_QUERY_RESOURCE = "legacy/WarehouseStockListCountQuery.sql";
    private static final String BY_SKU_QUERY_RESOURCE = "legacy/WarehouseStockBySkuQuery.sql";

    private final NamedParameterJdbcTemplate legacyJdbc;
    private final String listSql;
    private final String listCountSql;
    private final String bySkuSql;

    public WarehouseStockReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
        this.listSql = loadSql(LIST_QUERY_RESOURCE);
        this.listCountSql = loadSql(LIST_COUNT_QUERY_RESOURCE);
        this.bySkuSql = loadSql(BY_SKU_QUERY_RESOURCE);
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyWarehouseStockRow> findList(WarehouseStockListFilter filter, int limit, int offset) {
        return legacyJdbc.query(listSql, toParams(filter).addValue("limit", limit).addValue("offset", offset),
                WarehouseStockReadRepository::toRow);
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
}
