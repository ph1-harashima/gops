package com.glv.gsysportal.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** GET /api/orders/{id}/responses (Phase 7-C5 20章/21章 - Order Detail's/
 * Supplier Response screen's browsable Response History, Response1/
 * Response2/...). Every row but the CURRENT one (matching the Order's
 * {@code currentRevisionNo}) is READ ONLY history. */
public record SupplierResponseHistoryEntry(
        Long responseId,
        int revisionNo,
        LocalDate responseDate,
        String responseStatus,
        boolean isCurrent,
        String agreedBy,
        OffsetDateTime agreedAt,
        String reopenedBy,
        OffsetDateTime reopenedAt,
        String reopenReason
) {
}
