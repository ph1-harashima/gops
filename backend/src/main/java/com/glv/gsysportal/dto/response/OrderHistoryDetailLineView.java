package com.glv.gsysportal.dto.response;

import java.time.LocalDate;
import java.util.List;

/** The Recommended -> Ordered -> Confirmed 3-stage line (implementation
 * instructions 25章). {@code confirmedQty} is null whenever no Supplier
 * Response line exists yet for this Order (e.g. still DRAFT/READY_TO_ORDER)
 * or the line has not been answered - the same null/0 distinction as
 * elsewhere in Step 4.
 *
 * <p>Gap Analysis B-1 (docs/gulliver-20260917-phase1-gap-analysis.md 5章):
 * {@code currentStock}/{@code monthlySales}/{@code leadTime}/{@code openArrival}
 * are live Legacy READ ONLY values (same {@code LegacyStockReadRepository.findBySkus}
 * source SKU Detail/Candidate List already use - Fact, no new calculation),
 * so the Approval screen carries the same judgement material the person who
 * placed the order already saw. Null when the SKU can no longer be resolved
 * in Legacy (e.g. since discontinued/removed from Master) - never defaulted
 * to 0, matching this DTO's existing null/0 discipline for confirmedQty. */
public record OrderHistoryDetailLineView(
        String sku,
        String itemName,
        int recommendedQty,
        int orderedQty,
        Integer confirmedQty,
        LocalDate requestedDelivery,
        LocalDate confirmedDelivery,
        List<AttentionSummary> attentions,
        Integer currentStock,
        Integer monthlySales,
        String leadTime,
        Integer openArrival,
        /** Post-Freeze Business Refinement (re-audit doc §10-4): lets the
         * Approver see "欠品だがいつ入るのか" without leaving the Approval
         * screen. Same merged Display-Priority value as Candidate List/SKU
         * Detail - {@code SkuRestockExpectationResponse.SOURCE_*}. */
        String restockSource,
        LocalDate restockDate,
        /** Post-Freeze Business Refinement 2
         * (docs/gops-manufacturer-stockout-information-management.md §16) -
         * lets the Approver see the Manufacturer's own account (not just
         * Legacy's ETA) without leaving Approval. */
        String stockoutStatus,
        Integer shortageQty,
        LocalDate informationReceivedDate,
        String contactMethod,
        boolean restockHasConflict
) {
}
