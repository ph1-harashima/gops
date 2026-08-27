package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

/** GET /api/orders/{id}/events (implementation instructions 26章). Internal
 * codes only (eventType/fieldName) - Frontend resolves Japanese labels via
 * i18n (Requirements MD 30.12). */
public record AuditEventView(
        String eventType,
        Long portalOrderDetailId,
        String fieldName,
        String oldValue,
        String newValue,
        String performedBy,
        OffsetDateTime performedAt
) {
}
