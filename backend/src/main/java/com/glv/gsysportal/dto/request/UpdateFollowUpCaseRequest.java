package com.glv.gsysportal.dto.request;

/** PUT /api/follow-up-cases/{id} (Phase 7-C7A 20章: OPERATOR may update the
 * Note). Only {@code note} is editable this Phase - {@code reason}/{@code
 * skuCode} are fixed at creation. */
public record UpdateFollowUpCaseRequest(
        String note
) {
}
