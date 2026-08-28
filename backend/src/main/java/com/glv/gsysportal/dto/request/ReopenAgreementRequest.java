package com.glv.gsysportal.dto.request;

/** POST /api/orders/{id}/responses/{responseId}/reopen (Phase 7-C5 18章).
 * {@code reason} is mandatory (400 REOPEN_REASON_REQUIRED otherwise). */
public record ReopenAgreementRequest(
        String reason
) {
}
