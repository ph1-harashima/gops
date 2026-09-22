package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

/**
 * Stage 5K: ADMIN-only Refresh history/status row ({@code
 * DashboardAdminController}) - Stage 5J §19 Observability's "an ADMIN-only
 * read endpoint can simply query {@code dashboard_refresh_run ORDER BY
 * calculation_started_at DESC}", exposed as an API. Never shown to a
 * general Operator (Stage 5J §14/§18: only a timestamp, never failure
 * detail, appears on the plain Dashboard).
 */
public record DashboardRefreshRunResponse(
        Long id,
        String status,
        String triggerType,
        OffsetDateTime calculationStartedAt,
        OffsetDateTime calculationCompletedAt,
        Integer evaluatedItemCount,
        Integer candidateCount,
        int formulaErrorCount,
        String errorMessage,
        String initiatedBy
) {
}
