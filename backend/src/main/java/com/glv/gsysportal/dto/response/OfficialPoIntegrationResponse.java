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
 */
public record OfficialPoIntegrationResponse(
        Long orderId,
        int revisionNo,
        String status,
        String officialPoNo,
        String requestedBy,
        OffsetDateTime requestedAt,
        OfficialPoPreflightResult preflight,
        OffsetDateTime generatedAt,
        OffsetDateTime submittedAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime failedAt,
        String errorCode,
        String errorMessage
) {
    public static final String STATUS_NOT_REQUESTED = "NOT_REQUESTED";

    public static OfficialPoIntegrationResponse notRequested(Long orderId) {
        return new OfficialPoIntegrationResponse(orderId, 0, STATUS_NOT_REQUESTED,
                null, null, null, null, null, null, null, null, null, null);
    }
}
