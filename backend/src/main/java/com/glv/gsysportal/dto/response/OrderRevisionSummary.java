package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/orders/{id}/revisions (Phase 7-C5 19章/20章 - Order Detail's
 * browsable Revision History, Rev1/Rev2/...).
 *
 * {@code createdBy} is, and remains, the persisted Login ID exactly as
 * stored. {@code createdByDisplayName} is an additional, read-time-only
 * lookup against the current portal_user.display_name (same idiom as
 * AuditEventView.performedByDisplayName, Phase 7-H) - Frontend prefers this
 * for display and falls back to createdBy when null. */
public record OrderRevisionSummary(
        Long revisionId,
        int revisionNo,
        String revisionType,
        String reason,
        String createdBy,
        String createdByDisplayName,
        OffsetDateTime createdAt,
        List<OrderRevisionLineView> lines
) {
}
