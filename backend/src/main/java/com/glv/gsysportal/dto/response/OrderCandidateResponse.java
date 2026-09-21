package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Order Candidate List row.
 *
 * IMPORTANT: no Japanese display text is included here (Requirements MD 30.12).
 * itemStatus/dataSource are internal English codes; the Frontend resolves them
 * to Japanese via react-i18next resources.
 */
public record OrderCandidateResponse(
        String sku,
        String itemName,
        String brandCode,
        String brandName,
        String supplierCode,
        String supplierName,
        Integer currentStock,
        Integer safetyStock,
        Integer openPo,
        Integer monthlySales,
        String leadTime,
        Integer recommendedQty,
        String itemStatus,
        /** Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
         * gops-stage3-real-data-compatibility-review.md §5/§C6): real
         * Production data shows genuinely-discontinued items rarely carry
         * a recognizable itemStatus string (mostly NULL/blank/"NEW") - the
         * Frontend must not rely on itemStatus alone to flag discontinued
         * items. Propagated straight from MS_ITEM.DISCON, never inferred. */
        Boolean discon,
        BigDecimal unitPrice,
        String currency,
        String dataSource,
        /** BR-09 (docs/gulliver-20260917-confirmed-business-rules.md):
         * DOMESTIC/OVERSEAS/null(unclassified) - lets the Frontend show
         * "国内向け推奨数量計算ルールは設定準備中" specifically for a
         * DOMESTIC Supplier whose {@code recommendedQty} came back null,
         * rather than conflating that with any other null-Recommended-Qty
         * reason. */
        String regionClassification,
        /** Post-Freeze Business Refinement (re-audit doc §8/§11): the
         * merged Display-Priority result - {@code SkuRestockExpectationResponse.SOURCE_*}
         * (LEGACY_EXPECTED_ARRIVAL/PORTAL_MANUAL/PORTAL_MANUAL_UNKNOWN/NONE). */
        String restockSource,
        LocalDate restockDate,
        /** Post-Freeze Business Refinement 2
         * (docs/gops-manufacturer-stockout-information-management.md §15) -
         * Manufacturer Stockout Information, always distinct from the
         * merged restockSource/restockDate above (System Information vs
         * Manufacturer Information, requirements doc §3). */
        String stockoutStatus,
        LocalDate informationReceivedDate,
        String contactMethod,
        boolean restockHasConflict
) {
}
