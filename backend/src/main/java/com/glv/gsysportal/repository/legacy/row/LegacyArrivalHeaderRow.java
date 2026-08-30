package com.glv.gsysportal.repository.legacy.row;

import java.time.LocalDate;

/**
 * Mirrors Legacy TR_ARR (reduced columns, Phase 8-G - see
 * backend/demo-data/01-schema.sql's tr_arr comment for the exact Source
 * basis). PK is (supplierCd, poNo, invNo), matching real TrArrPK exactly.
 * {@code qty} is TR_ARR's own single header-level quantity - a Source Fact
 * distinct from (and never reconciled against) the per-SKU
 * Ordered/Invoiced/Stock-In sums {@link ArrivalReadRepository} derives
 * separately from tr_po_dtl/tr_inv_dtl (docs/legacy-warehouse-logistics-
 * logizero-reverse-engineering.md 7章/11章 - the two are different-granularity
 * Source Facts, never compared against each other).
 *
 * <p>{@code whRepStatus}/{@code whRepResult} are passed through byte-for-byte
 * from Legacy - free-text/code values with no Portal-invented meaning
 * (Phase 8-G 4章's explicit instruction: "Legacyに存在しないStatusを作らない").
 */
public record LegacyArrivalHeaderRow(
        String supplierCd,
        String poNo,
        String invNo,
        String brandCd,
        String brandName,
        String supplierName,
        String blNo,
        String vesselNo,
        Integer qty,
        LocalDate etd,
        LocalDate eta,
        LocalDate etaWh,
        LocalDate stkInDate,
        String whRepStatus,
        String whRepResult,
        Integer orderedQty,
        Integer invoiceQty,
        Integer stockInQty) {
}
