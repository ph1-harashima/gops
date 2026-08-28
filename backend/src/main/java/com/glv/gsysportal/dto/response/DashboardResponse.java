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
        int awaitingSupplierCount,
        int attentionCount,
        List<DashboardBrandRow> brands
) {
}
