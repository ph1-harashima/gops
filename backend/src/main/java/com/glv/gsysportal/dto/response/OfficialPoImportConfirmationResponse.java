package com.glv.gsysportal.dto.response;

import java.util.List;

/**
 * Phase 9-C: result of one "G-SYS取込確認" check
 * (docs/official-po-integration-detailed-design.md §9's Success Detection,
 * implemented as a manual on-demand READ ONLY check rather than a
 * background poller - this environment cannot reach a real Legacy
 * Production DB regardless, and a scheduled job reaching for network
 * resources on its own is exactly the kind of thing §10 asks to avoid).
 *
 * <p>{@code matched=true} means the Integration Request was just
 * transitioned to CONFIRMED (or already was) - {@code reason}/{@code details}
 * are then both empty. {@code matched=false} means no state change occurred;
 * {@code reason} is {@value #REASON_NOT_YET_IMPORTED} (Legacy has no
 * matching {@code TR_PO} yet - not an error, the Batch may simply not have
 * run) or {@value #REASON_MISMATCH} ({@code TR_PO} exists but its
 * {@code TR_PO_DTL} lines don't match Portal's current Order lines -
 * {@code details} explains how).
 *
 * <p>{@code integration} carries the (possibly just-updated) Integration
 * state so the caller never needs a second GET to refresh the UI.
 */
public record OfficialPoImportConfirmationResponse(
        boolean matched,
        String reason,
        List<OfficialPoImportConfirmationDiff> details,
        OfficialPoIntegrationResponse integration
) {
    public static final String REASON_NOT_YET_IMPORTED = "NOT_YET_IMPORTED";
    public static final String REASON_MISMATCH = "MISMATCH";
}
