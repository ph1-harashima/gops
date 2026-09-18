package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 7-C2A 13章: shared shape for both GET (view) and POST (request)
 * {@code /api/orders/{id}/official-po*}. {@code status = "NOT_REQUESTED"} is
 * synthesized by the Service when no {@code official_po_integration_request}
 * row exists yet (never a persisted value - see
 * {@code OfficialPoIntegrationRequest}'s Javadoc) - every other field is null
 * in that case.
 *
 * <p>Phase 9-A adds the Excel-contract fields (Delivery Week/Date, Ship Via/
 * Term, Payment Term) and {@code excelGenerated} (whether a download is currently
 * available - derived from {@code generatedFileKey != null}, not exposing
 * the storage key itself to the Frontend).
 */
public record OfficialPoIntegrationResponse(
        Long orderId,
        int revisionNo,
        String status,
        String officialPoNo,
        String requestedBy,
        /** i18n localization audit (matches {@code AuditEventView.performedByDisplayName}'s
         * documented idiom): read-time-only lookup against portal_user.display_name
         * for {@code requestedBy} - Frontend prefers this for display, falling
         * back to requestedBy when null. Never changes what is persisted. */
        String requestedByDisplayName,
        OffsetDateTime requestedAt,
        OfficialPoPreflightResult preflight,
        OffsetDateTime generatedAt,
        OffsetDateTime submittedAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime failedAt,
        String errorCode,
        String errorMessage,
        /** Phase 7-C6 12章/13章: NEW/UPDATE, or null until an ADMIN explicitly sets it. */
        String integrationIntent,
        String deliveryWeek,
        String deliveryDate,
        String shipVia,
        String shipTerm,
        String paymentTerm,
        boolean excelGenerated
) {
    public static final String STATUS_NOT_REQUESTED = "NOT_REQUESTED";

    public static OfficialPoIntegrationResponse notRequested(Long orderId) {
        return new OfficialPoIntegrationResponse(
                orderId, 0, STATUS_NOT_REQUESTED,
                null, // officialPoNo
                null, // requestedBy
                null, // requestedByDisplayName
                null, // requestedAt
                null, // preflight
                null, // generatedAt
                null, // submittedAt
                null, // confirmedAt
                null, // failedAt
                null, // errorCode
                null, // errorMessage
                null, // integrationIntent
                null, // deliveryWeek
                null, // deliveryDate
                null, // shipVia
                null, // shipTerm
                null, // paymentTerm
                false // excelGenerated
        );
    }
}
