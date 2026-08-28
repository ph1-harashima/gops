package com.glv.gsysportal.service.excel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Phase 7-C2A 8章: pure input to {@link OfficialPoExcelGenerator} - the
 * Portal Order / Legacy Master Snapshot data the Generator needs, already
 * assembled by the caller. The Generator itself never queries Legacy or
 * writes any File - see its own Javadoc.
 *
 * <p>{@code officialPoNo} is deliberately the caller's responsibility to
 * supply - this Phase has no real path that produces a non-test value (7-C2A
 * 7章/9章's Gate). Unit/Contract Tests may pass a Test-only value; no
 * Production/Demo code path may ever pass a value here that gets persisted
 * as a real Official PO No. (7-C2A 9章).
 */
public record OfficialPoExcelInput(
        String officialPoNo,
        String supplierCode,
        String brandCode,
        String brandName,
        LocalDate orderDate,
        String deliveryWeek,
        String deliveryDate,
        String shipVia,
        String shipTerm,
        String paymentTerm,
        List<Line> lines
) {
    public record Line(
            String itemCode,
            String series,
            String modelNo,
            String model,
            String color,
            String description,
            int qty,
            BigDecimal unitPrice,
            /** Excel currency symbol (e.g. "$", "¥") - resolved by the
             * caller from the Legacy Currency Master (MS_COMM/MS_CCY), never
             * by the Generator itself (7-C2A 8章 - "Legacy Master Snapshot"
             * is an INPUT). Encoded into the Unit Price Cell's Number Format,
             * matching PrOfficialPoImportBatch.getCcyCode()'s expectation
             * that Currency is read from Cell format, not Cell value
             * (docs/official-po-integration-detailed-design.md 3.4章). */
            String currencySymbol,
            String countryOfOrigin,
            BigDecimal boxHeight,
            BigDecimal boxWidth,
            BigDecimal boxDepth,
            BigDecimal weight
    ) {
    }
}
