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
        String errorCode,
        String errorMessage,
        int retryCount
) {
    public static OrderEmailResponse notSent(Long orderId) {
        return new OrderEmailResponse(orderId, 0, null, List.of(), List.of(), null, null, null, null, null, 0);
    }
}
