package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

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
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt,
        String closedBy,
        OffsetDateTime closedAt
) {
}
