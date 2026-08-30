package com.glv.gsysportal.dto.response;

import java.util.List;

/**
 * GET /api/dashboard (implementation instructions Step 5 3章). An Action /
 * Operation Cockpit, deliberately NOT an Analytics screen - no Sales Trend,
 * no margin/profit figures, no inventory turnover rate. Every count here is
 * derived from data already exposed by the existing Candidate/Draft/
 * Supplier Response/Attention APIs, never a fabricated metric.
 */
public record DashboardResponse(
        int candidateCount,
        int outOfStockCount,
        int longTermOutOfStockCount,
        int draftCount,
        /** Phase 7-C1 14章: ADMIN approval queue KPI. */
        int pendingApprovalCount,
        int awaitingSupplierCount,
        int attentionCount,
        /** Phase 7-C7A 19章: Open Follow-up Case count (OPEN + INQUIRY_PREPARED) -
         * no "納期超過" KPI exists (no due-date/threshold concept is defined
         * anywhere in this codebase yet, 19章's explicit caution against
         * inventing one). */
        int openFollowUpCaseCount,
        /** Phase 8-J 11章/13章: Price Change Sets currently in DRAFT status -
         * added because Dashboard's information design predated Phase 8-B/
         * 8-D (Price Change Foundation) and had zero entry point into it
         * until this Phase. */
        int priceChangeDraftCount,
        List<DashboardBrandRow> brands
) {
}
