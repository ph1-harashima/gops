package com.glv.gsysportal.dto.response;

/** One row of the Brand別 breakdown table (implementation instructions
 * Step 5 3章). Clicking a row navigates the Frontend to the corresponding
 * screen pre-filtered by brandCode + the relevant Status/condition. */
public record DashboardBrandRow(
        String brandCode,
        String brandName,
        int candidateCount,
        int outOfStockCount,
        /** IA Phase 3 (docs/gops-order-candidates-brand-entry-implementation.md):
         * Requirements MD §10's Brand一覧 table always specified Long-term
         * OOS alongside OOS - added here (same predicate the Dashboard-wide
         * KPI already uses) so the Order Candidates Brand List can reuse
         * this one Response, never a second Dashboard-like endpoint. */
        int longTermOutOfStockCount,
        int draftCount,
        int awaitingSupplierCount,
        int attentionCount
) {
}
