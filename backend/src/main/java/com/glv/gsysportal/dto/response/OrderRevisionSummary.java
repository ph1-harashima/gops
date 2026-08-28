package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/orders/{id}/revisions (Phase 7-C5 19章/20章 - Order Detail's
 * browsable Revision History, Rev1/Rev2/...). */
public record OrderRevisionSummary(
        Long revisionId,
        int revisionNo,
        String revisionType,
        String reason,
        String createdBy,
        OffsetDateTime createdAt,
        List<OrderRevisionLineView> lines
) {
}
