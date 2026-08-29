package com.glv.gsysportal.repository.legacy;

import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyHeaderRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyLineRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Legacy G-SYS Adapter - READ ONLY (same 3-layer guarantee as
 * {@link LegacyStockReadRepository}/{@link OfficialPoPreflightReadRepository}/
 * {@link FulfillmentReadRepository}, Technical Design 4.1). Phase 7-C6
 * (Excel / Legacy Concurrency Control Foundation) 0章's Source audit.
 *
 * <p><b>Why this is a new Repository rather than reusing
 * {@link FulfillmentReadRepository}</b> (7-C6 0章's explicit "既存Repository
 * を可能な限り再利用する" instruction, evaluated): both query the same
 * TR_PO/TR_PO_DTL tables, but the column needs are disjoint and largely
 * non-overlapping - Fulfillment only ever needs {@code status}/{@code qty_po}
 * (enough to compute Ordered/Invoiced/Stock-in/Outstanding); Concurrency
 * Detection needs the FULL commercial header/line snapshot (supplier/brand/
 * order date/currency/delivery/total amount/unit price - 7-C6 2章's candidate
 * field list) so it can detect ANY meaningful change, not just a quantity
 * change. Widening {@link FulfillmentReadRepository}'s row types to carry
 * these extra columns for every caller (most of whom don't need them) was
 * judged worse than a second, small, purpose-built Repository against the
 * same tables - this mirrors how {@link OfficialPoPreflightReadRepository}
 * and {@link FulfillmentReadRepository} already coexist as independent,
 * Phase-specific Repositories rather than one shared mega-Repository. Reuse
 * happens at the pattern level instead: same {@code NamedParameterJdbcTemplate},
 * same {@code legacyTransactionManager}, same {@code del_flg} soft-delete
 * filter convention, same READ ONLY guarantee.
 *
 * <p><b>Line identity = SKU (item_cd), never TR_PO_DTL.line_no</b> - confirmed
 * via PrOfficialPoImportBatch's re-Import code path (line 952: {@code int
 * lineNo = 1;}, incremented per row in whatever order that Import run's rows
 * happen to appear in): {@code line_no} is reassigned from scratch on every
 * Delete & Recreate re-Import and is NOT a stable cross-Import business
 * identity - only {@code item_cd} is. This is exactly the 7-C6 29章 STOP
 * condition candidate "Delete & RecreateでLine Identityを追跡不能" -
 * evaluated and found NOT triggered precisely because this Repository (and
 * the Canonical Snapshot / Diff Engine built on top of it) deliberately never
 * depends on {@code line_no} for identity, only {@code item_cd}. If the same
 * {@code item_cd} legitimately appears on more than one line within a single
 * PO (not expected in practice - one Excel row per item), the Snapshot
 * Factory aggregates (sums qty, keeps the first-seen unit price) rather than
 * losing a line - see {@code LegacyPoSnapshotFactory}'s Javadoc.
 */
@Repository
public class LegacyPoConcurrencyReadRepository {

    private final NamedParameterJdbcTemplate legacyJdbc;

    public LegacyPoConcurrencyReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public Optional<LegacyPoConcurrencyHeaderRow> findPoHeader(String officialPoNo) {
        List<LegacyPoConcurrencyHeaderRow> rows = legacyJdbc.query(
                "SELECT po_no, status, po_type, supplier_cd, brand_cd, ordr_date, ccy, deliv_week, deliv_date, amt_ttl "
                        + "FROM tr_po WHERE po_no = :poNo AND (del_flg IS NULL OR del_flg = 0)",
                new MapSqlParameterSource("poNo", officialPoNo),
                (rs, rowNum) -> new LegacyPoConcurrencyHeaderRow(
                        rs.getString("po_no"), rs.getString("status"), rs.getString("po_type"),
                        rs.getString("supplier_cd"), rs.getString("brand_cd"),
                        rs.getObject("ordr_date", java.time.LocalDate.class),
                        rs.getString("ccy"), rs.getString("deliv_week"), rs.getString("deliv_date"),
                        rs.getBigDecimal("amt_ttl")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Only the Official PO's OWN lines - a linked Credit PO (if any) is a
     * separate {@code po_no} and is never queried here (Concurrency Detection
     * is scoped to exactly the PO No. Portal is tracking, unlike Fulfillment's
     * deliberate Original+Credit netting). Ordered by {@code item_cd} in SQL
     * as a first pass - the Snapshot Factory re-sorts in Java regardless
     * (defense in depth, 7-C6 2章's Canonical Ordering requirement must not
     * depend on the DB's own ORDER BY guarantee alone). */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyPoConcurrencyLineRow> findPoLines(String officialPoNo) {
        return legacyJdbc.query(
                "SELECT item_cd, qty_po, prc_unit FROM tr_po_dtl "
                        + "WHERE po_no = :poNo AND (del_flg IS NULL OR del_flg = 0) ORDER BY item_cd",
                new MapSqlParameterSource("poNo", officialPoNo),
                (rs, rowNum) -> new LegacyPoConcurrencyLineRow(
                        rs.getString("item_cd"), (Integer) rs.getObject("qty_po"), rs.getBigDecimal("prc_unit")));
    }
}
