package com.glv.gsysportal.repository.legacy;

import com.glv.gsysportal.repository.legacy.row.LegacyArrivalHeaderRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Legacy G-SYS Adapter - READ ONLY (Phase 8-G, Arrival Visibility Foundation
 * - same three-layer guarantee as every other class in this package,
 * Technical Design 4.1). Backed entirely by {@code tr_arr} (Phase 8-G
 * addition to backend/demo-data/01-schema.sql) joined against
 * {@code tr_po_dtl}/{@code tr_inv_dtl} for per-header Qty sums - see
 * ArrivalListQuery.sql's own header comment for the exact derivation and its
 * Source basis (docs/legacy-warehouse-logistics-logizero-reverse-
 * engineering.md 7章/11章/13章).
 *
 * <p><b>Never joins {@code ms_stk} anywhere</b> - Phase 8-G 11章's most
 * important constraint. Warehouse Stock is read exclusively by the separate
 * {@link WarehouseStockReadRepository}; this class has no method that could
 * even accidentally combine the two.
 */
@Repository
public class ArrivalReadRepository {

    private static final String LIST_QUERY_RESOURCE = "legacy/ArrivalListQuery.sql";
    private static final String LIST_COUNT_QUERY_RESOURCE = "legacy/ArrivalListCountQuery.sql";

    private final NamedParameterJdbcTemplate legacyJdbc;
    private final String listSql;
    private final String listCountSql;

    public ArrivalReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
        this.listSql = loadSql(LIST_QUERY_RESOURCE);
        this.listCountSql = loadSql(LIST_COUNT_QUERY_RESOURCE);
    }

    /** Arrival List (Phase 8-G 5章) - Backend-paginated, Backend-filtered. */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyArrivalHeaderRow> findList(ArrivalListFilter filter, int limit, int offset) {
        return legacyJdbc.query(listSql, toParams(filter).addValue("limit", limit).addValue("offset", offset),
                ArrivalReadRepository::toHeaderRow);
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public long countList(ArrivalListFilter filter) {
        Long count = legacyJdbc.queryForObject(listCountSql, toParams(filter), Long.class);
        return count == null ? 0L : count;
    }

    /** Arrival Detail's header row (Phase 8-G 6章) - same shape as one
     * {@link #findList} row, fetched by its exact composite key. */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public Optional<LegacyArrivalHeaderRow> findOne(String supplierCd, String poNo, String invNo) {
        ArrivalListFilter filter = new ArrivalListFilter(supplierCd, null, poNo, invNo, null, null, null);
        // Reuses the List query's own WHERE-shape via exact (non-LIKE) match
        // on all 3 PK columns - poNumber/invoiceNumber below intentionally
        // do NOT go through the Filter's LIKE-wildcard path (see toParams).
        List<LegacyArrivalHeaderRow> rows = legacyJdbc.query(listSql,
                toParams(filter).addValue("poNumberLike", poNo).addValue("invoiceNumberLike", invNo)
                        .addValue("limit", 1).addValue("offset", 0),
                ArrivalReadRepository::toHeaderRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static MapSqlParameterSource toParams(ArrivalListFilter f) {
        return new MapSqlParameterSource()
                .addValue("supplierCode", f.supplierCode())
                .addValue("brandCode", f.brandCode())
                .addValue("poNumber", f.poNumber())
                .addValue("poNumberLike", f.poNumber() == null ? null : "%" + f.poNumber() + "%")
                .addValue("invoiceNumber", f.invoiceNumber())
                .addValue("invoiceNumberLike", f.invoiceNumber() == null ? null : "%" + f.invoiceNumber() + "%")
                .addValue("skuKeyword", f.skuKeyword())
                .addValue("skuKeywordLike", f.skuKeyword() == null ? null : "%" + f.skuKeyword() + "%")
                .addValue("arrivalDateFrom", f.arrivalDateFrom())
                .addValue("arrivalDateTo", f.arrivalDateTo());
    }

    private static LegacyArrivalHeaderRow toHeaderRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new LegacyArrivalHeaderRow(
                rs.getString("supplier_cd"),
                rs.getString("po_no"),
                rs.getString("inv_no"),
                rs.getString("brand_cd"),
                rs.getString("brand_name"),
                rs.getString("supplier_name"),
                rs.getString("bl_no"),
                rs.getString("vessel_no"),
                nullableInt(rs, "arrival_qty"),
                rs.getObject("etd", LocalDate.class),
                rs.getObject("eta", LocalDate.class),
                rs.getObject("eta_wh", LocalDate.class),
                rs.getObject("stk_in_date", LocalDate.class),
                rs.getString("wh_rep_status"),
                rs.getString("wh_rep_result"),
                nullableInt(rs, "ordered_qty"),
                nullableInt(rs, "invoice_qty"),
                nullableInt(rs, "stock_in_qty"));
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        return ((Number) value).intValue();
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

    /** Arrival List's Filter (Phase 8-G 5章's minimum candidate list). All
     * fields optional/nullable - an all-null Filter returns every row. */
    public record ArrivalListFilter(
            String supplierCode,
            String brandCode,
            String poNumber,
            String invoiceNumber,
            String skuKeyword,
            LocalDate arrivalDateFrom,
            LocalDate arrivalDateTo) {
    }
}
