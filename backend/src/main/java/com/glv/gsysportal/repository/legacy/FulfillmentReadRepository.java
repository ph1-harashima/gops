package com.glv.gsysportal.repository.legacy;

import com.glv.gsysportal.repository.legacy.row.LegacyInvoiceLineRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoHeaderRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoLineRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Legacy G-SYS Adapter - READ ONLY (same guarantee as
 * {@link LegacyStockReadRepository}/{@link OfficialPoPreflightReadRepository},
 * Technical Design 4.1). Phase 7-C7A 0章's Source audit findings, precisely:
 *
 * <p><b>TR_PO/TR_PO_DTL</b>: {@code status} reaches INITIAL -> OFFICIAL as the
 * normal terminus for real merchandise POs (confirmed via
 * PrOfficialPoImportBatch); a third value STOCKIN exists in {@code Const} but
 * is only ever written by PrBLInvImportBatch's "dummy PO for a bill-only/
 * deposit transaction with no real item line" edge case (ITEM_CD_DUMMY) - not
 * a normal Fulfillment terminus, and irrelevant to this Repository (which
 * only ever reads real item-code PO lines).
 *
 * <p><b>TR_INV/TR_INV_DTL</b>: {@code TR_INV.status} is TRANSIT (set at
 * creation by PrOfficialPoImportBatch's embedded invoice column) ->
 * RECEIVING (BL Invoice / Stock-in Report re-affirm) -> STOCK_IN (once every
 * line's stock-in wait-qty is resolved). {@code TR_INV_DTL.qty} is the
 * invoiced/transit qty; {@code qty_stk_in} is the actual physically-received
 * qty, set by PrStkInReportImportBatch.
 *
 * <p><b>Credit PO (discrepancy reconciliation)</b>: when Stock-in Report
 * Import finds {@code stock-in qty != invoiced qty} for a line,
 * PrStkInReportImportBatch (a) caps the ORIGINAL TR_INV_DTL.qty_stk_in at its
 * own {@code qty} (never exceeds it) and (b) creates/updates a linked
 * "Credit" PO+Invoice - PO No. {@code "{originalPoNo}-{arrCode}"}, PO_TYPE=
 * CREDIT (confirmed via {@code BusinessLogicUtil.creditPoNo}/{@code creditInvNo})
 * - whose own TR_INV_DTL.qty_stk_in holds the exact signed difference
 * (negative for a shortage, positive for an overage). <b>The TRUE physical
 * stock-in qty for an item is therefore the SUM of qty_stk_in across the
 * Original row AND its linked Credit row(s), never the Original row alone</b>
 * (7-C7A 28章's "Credit POを含めないと正しいOutstandingを算出できない" STOP
 * condition was evaluated against this exact mechanism and found tractable -
 * see docs/fulfillment-follow-up-foundation.md 2章 for the full reasoning).
 * {@link #findInvoiceLines} deliberately returns BOTH Original and any
 * linked Credit rows together (via a {@code po_no = :officialPoNo OR po_no
 * LIKE :officialPoNo || '-%'} match) so the caller can net them - it does
 * NOT use {@code BusinessLogicUtil}'s own {@code nonCreditPoNo}/
 * {@code nonCreditInvNo} reverse-lookup helpers, which this Phase's audit
 * found to be a dead code path in Legacy itself (they strip a
 * {@code "-#"} delimiter that {@code creditPoNo}'s own forward formula -
 * {@code poNo + "-" + arrCode} - never actually produces); this Repository
 * only ever needs the forward direction, so that inconsistency is avoided
 * entirely rather than replicated.
 *
 * <p><b>MS_STK "Open PO" columns</b> (PO_NO_1..20/PO_QTY_1..20/ARR_QTY_1..20)
 * are a separate, ITEM-level (not PO-level) denormalized cache of "which POs
 * currently have outstanding qty for this item across every Warehouse" -
 * deliberately NOT read by this Repository, which answers a narrower
 * question ("Outstanding for THIS ONE Official PO") that MS_STK's own
 * aggregate does not directly represent. The two are related-but-different
 * concepts, not a contradiction (7-C7A 28章's second STOP condition
 * evaluated and not triggered - see docs/fulfillment-follow-up-foundation.md
 * 2章).
 */
@Repository
public class FulfillmentReadRepository {

    private final NamedParameterJdbcTemplate legacyJdbc;

    public FulfillmentReadRepository(NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate) {
        this.legacyJdbc = legacyNamedParameterJdbcTemplate;
    }

    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public Optional<LegacyPoHeaderRow> findPoHeader(String officialPoNo) {
        List<LegacyPoHeaderRow> rows = legacyJdbc.query(
                "SELECT po_no, status, po_type, supplier_cd, brand_cd FROM tr_po "
                        + "WHERE po_no = :poNo AND (del_flg IS NULL OR del_flg = 0)",
                new MapSqlParameterSource("poNo", officialPoNo),
                (rs, rowNum) -> new LegacyPoHeaderRow(
                        rs.getString("po_no"), rs.getString("status"), rs.getString("po_type"),
                        rs.getString("supplier_cd"), rs.getString("brand_cd")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Only the Official PO's OWN lines - never a linked Credit PO's lines
     * (a Credit PO's TR_PO_DTL.qty_po mirrors its Invoice-side discrepancy
     * adjustment, not a fresh order quantity; see this class's Javadoc). */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyPoLineRow> findPoLines(String officialPoNo) {
        return legacyJdbc.query(
                "SELECT item_cd, qty_po FROM tr_po_dtl "
                        + "WHERE po_no = :poNo AND (del_flg IS NULL OR del_flg = 0) ORDER BY line_no",
                new MapSqlParameterSource("poNo", officialPoNo),
                (rs, rowNum) -> new LegacyPoLineRow(rs.getString("item_cd"), (Integer) rs.getObject("qty_po")));
    }

    /** Original PO's invoice lines PLUS any linked Credit PO's invoice lines
     * (see this class's Javadoc on why both are needed and returned
     * together, distinguished by each row's own {@code poNo}). */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<LegacyInvoiceLineRow> findInvoiceLines(String supplierCode, String officialPoNo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("supplierCd", supplierCode)
                .addValue("poNo", officialPoNo)
                .addValue("poNoPrefix", officialPoNo + "-%");
        return legacyJdbc.query(
                "SELECT item_cd, qty, qty_stk_in, po_no FROM tr_inv_dtl "
                        + "WHERE supplier_cd = :supplierCd AND (po_no = :poNo OR po_no LIKE :poNoPrefix) "
                        + "AND (del_flg IS NULL OR del_flg = 0)",
                params,
                (rs, rowNum) -> new LegacyInvoiceLineRow(
                        rs.getString("item_cd"), (Integer) rs.getObject("qty"),
                        (Integer) rs.getObject("qty_stk_in"), rs.getString("po_no")));
    }
}
