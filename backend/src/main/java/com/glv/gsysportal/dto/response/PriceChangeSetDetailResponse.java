package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** Price Change Set Detail screen (target-price-change-workflow.md 15章
 * "Price Change Detail") - Change Set header + line list + Audit Trail, all
 * in one response (mirrors {@code OrderDraftDetailResponse}'s own shape). */
public record PriceChangeSetDetailResponse(
        Long id,
        String status,
        String note,
        String createdByDisplayName,
        OffsetDateTime createdAt,
        String updatedByDisplayName,
        OffsetDateTime updatedAt,
        List<PriceChangeSetLineResponse> details,
        List<PriceChangeAuditEventView> auditTrail) {
}
