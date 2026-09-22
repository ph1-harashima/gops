package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/dashboard (implementation instructions Step 5 3章). An Action /
 * Operation Cockpit, deliberately NOT an Analytics screen - no Sales Trend,
 * no margin/profit figures, no inventory turnover rate. Every count here is
 * derived from data already exposed by the existing Candidate/Draft/
 * Supplier Response/Attention APIs, never a fabricated metric.
 *
 * <p>Stage 5K (docs/real-data-audit/gops-stage5k-dashboard-read-model-implementation.md):
 * {@code candidateCount}/{@code outOfStockCount}/{@code
 * longTermOutOfStockCount} (overall and per-Brand) are now read from the
 * Portal DB Read Model - a background Refresh, not computed live per
 * request (Stage 5J §12: zero Legacy DB queries, zero {@code calc4}
 * invocations in this request path). {@code calculatedAt}/{@code
 * initialized} are new fields this Stage added for the Freshness UX
 * (Stage 5J §14; this Stage's own §13) - every other field's semantics
 * are unchanged.
 */
public record DashboardResponse(
        /** When the active Read Model version was published - Stage 5J
         * §14 "最終更新" timestamp. Null only when {@link #initialized} is
         * false. */
        OffsetDateTime calculatedAt,
        /** False only in the narrow startup/reset window before the very
         * first Refresh has ever succeeded (Stage 5J §15) - every count
         * below is 0 in that state, not a fabricated real value. The
         * Frontend must show an explicit "初期化中" state, never treat 0
         * as a real candidateCount, when this is false. */
        boolean initialized,
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
