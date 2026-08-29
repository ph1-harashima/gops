package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

/** Phase 7-C6 9章: result of the "G-SYS現在状態を基準として記録" Business Action. */
public record LegacyPoBaselineResponse(
        Long orderId,
        int revisionNo,
        String officialPoNo,
        String fingerprint,
        String capturedBy,
        OffsetDateTime capturedAt) {
}
