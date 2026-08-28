package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

/** Implementation instructions Step 5 2章. Attention rows are never deleted
 * (削除は禁止) - Acknowledge only flips is_active/resolved/acknowledged
 * fields, preserving full history. */
public record AttentionResponse(
        Long id,
        Long portalOrderId,
        Long portalOrderDetailId,
        String attentionType,
        boolean isActive,
        OffsetDateTime detectedAt,
        OffsetDateTime resolvedAt,
        String acknowledgedBy,
        OffsetDateTime acknowledgedAt
) {
}
