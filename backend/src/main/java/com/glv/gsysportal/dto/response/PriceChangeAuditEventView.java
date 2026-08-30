package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

/**
 * Phase 8-B Section 10: "ユーザーは既存PortalUserのdisplayNameを使うこと。
 * 内部usernameを画面表示へ漏らさない。" - unlike the Order-side
 * {@code AuditEventView} (which carries both the raw Login ID and the
 * resolved displayName), this record deliberately has NO raw-username field
 * at all, so there is nothing for a Frontend component to accidentally render.
 */
public record PriceChangeAuditEventView(
        String eventType,
        String fieldName,
        String oldValue,
        String newValue,
        String performedByDisplayName,
        OffsetDateTime performedAt,
        String note) {
}
