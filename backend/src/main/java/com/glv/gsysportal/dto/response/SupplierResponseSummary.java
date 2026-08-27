package com.glv.gsysportal.dto.response;

/** Implementation instructions 18章 Difference Summary - computed
 * server-side, never left to the Frontend to recompute independently. */
public record SupplierResponseSummary(
        int totalCount,
        int answeredCount,
        int unansweredCount,
        int quantityChangedCount,
        int deliveryChangedCount,
        int zeroQtyCount
) {
}
