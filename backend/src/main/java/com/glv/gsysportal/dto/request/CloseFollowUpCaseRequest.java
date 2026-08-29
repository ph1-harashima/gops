package com.glv.gsysportal.dto.request;

/** POST /api/follow-up-cases/{id}/close (Phase 7-C7A 20章: ADMIN only).
 * {@code note} is optional (no instruction mandates a Close reason, unlike
 * 7-C5's Reopen). */
public record CloseFollowUpCaseRequest(
        String note
) {
}
