package com.glv.gsysportal.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** GET /api/orders/{id}/responses (Phase 7-C5 20章/21章 - Order Detail's/
 * Supplier Response screen's browsable Response History, Response1/
 * Response2/...). Every row but the CURRENT one (matching the Order's
 * {@code currentRevisionNo}) is READ ONLY history.
 *
 * {@code agreedBy}/{@code reopenedBy} are, and remain, the persisted Login
 * ID exactly as stored. {@code agreedByDisplayName}/{@code
 * reopenedByDisplayName} are additional, read-time-only lookups against the
 * current portal_user.display_name (same idiom as
 * AuditEventView.performedByDisplayName, Phase 7-H) - Frontend prefers these
 * for display and falls back to the Login ID when null. */
public record SupplierResponseHistoryEntry(
        Long responseId,
        int revisionNo,
        LocalDate responseDate,
        String responseStatus,
        boolean isCurrent,
        String agreedBy,
        String agreedByDisplayName,
        OffsetDateTime agreedAt,
        String reopenedBy,
        String reopenedByDisplayName,
        OffsetDateTime reopenedAt,
        String reopenReason
) {
}
