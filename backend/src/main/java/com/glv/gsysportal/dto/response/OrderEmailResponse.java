package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** Phase 9-E: current Send state for an Order - {@code status=null} means
 * no Send has ever been attempted (no {@code order_email} row exists yet,
 * mirrors {@code OfficialPoIntegrationResponse.notRequested}'s own idiom). */
public record OrderEmailResponse(
        Long orderId,
        int revisionNo,
        String status,
        List<String> to,
        List<String> cc,
        String subject,
        OffsetDateTime sentAt,
        String sentBy,
        /** i18n localization audit (matches {@code AuditEventView.performedByDisplayName}'s
         * documented idiom) - Frontend prefers this for display, falling back
         * to sentBy when null. */
        String sentByDisplayName,
        String errorCode,
        String errorMessage,
        int retryCount,
        /** Gap Analysis C-5 (docs/gulliver-20260917-phase1-gap-analysis.md
         * 10章): the Master-resolved addresses, ALWAYS present regardless of
         * whether an Override was used - {@code to}/{@code cc} above stay
         * "what was actually sent". */
        List<String> masterTo,
        List<String> masterCc,
        boolean recipientOverrideUsed
) {
    public static OrderEmailResponse notSent(Long orderId) {
        return new OrderEmailResponse(orderId, 0, null, List.of(), List.of(), null, null, null, null, null, null, 0,
                List.of(), List.of(), false);
    }
}
