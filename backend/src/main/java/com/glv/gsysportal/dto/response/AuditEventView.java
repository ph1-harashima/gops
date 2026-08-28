package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

/** GET /api/orders/{id}/events (implementation instructions 26章). Internal
 * codes only (eventType/fieldName) - Frontend resolves Japanese labels via
 * i18n (Requirements MD 30.12).
 *
 * {@code performedBy} is, and remains, the persisted audit_event.performed_by
 * value (the Login ID) exactly as stored - this record never rewrites it.
 * {@code performedByDisplayName} is an additional, read-time-only lookup
 * against the current portal_user.display_name for that Login ID (i18n
 * localization audit, Frontend prefers this for display and falls back to
 * performedBy when null - e.g. no matching active user). This does not
 * change what is stored in audit_event. */
public record AuditEventView(
        String eventType,
        Long portalOrderDetailId,
        String fieldName,
        String oldValue,
        String newValue,
        String performedBy,
        String performedByDisplayName,
        OffsetDateTime performedAt
) {
}
