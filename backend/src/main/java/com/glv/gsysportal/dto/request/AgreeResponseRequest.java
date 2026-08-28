package com.glv.gsysportal.dto.request;

/**
 * POST /api/orders/{id}/responses/{responseId}/agree (Phase 7-C5 11章).
 * {@code forceAgree}: ADMIN-only override to Agree despite an unacknowledged
 * ACTIVE Attention still existing (409 UNACKNOWLEDGED_ATTENTION otherwise) -
 * this endpoint is already ADMIN-only end to end, so no separate role check
 * is needed for the flag itself.
 */
public record AgreeResponseRequest(
        boolean forceAgree
) {
}
