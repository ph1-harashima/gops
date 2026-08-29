package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

/** {@code createdBy} is, and remains, the persisted Login ID exactly as
 * stored. {@code createdByDisplayName} is an additional, read-time-only
 * lookup against the current portal_user.display_name (same idiom as
 * AuditEventView.performedByDisplayName, Phase 7-H) - Frontend prefers this
 * for display and falls back to createdBy when null. */
public record FollowUpCaseResponse(
        Long id,
        Long portalOrderId,
        Long orderRevisionId,
        String officialPoNo,
        String skuCode,
        String status,
        String reason,
        String note,
        String createdBy,
        String createdByDisplayName,
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt,
        String closedBy,
        OffsetDateTime closedAt
) {
}
