package com.glv.gsysportal.dto.request;

/** Phase 7-C1 12章: reason is mandatory (validated in the service so a
 * missing body field yields RETURN_REASON_REQUIRED, not a bare 400). */
public record ReturnForCorrectionRequest(String reason) {
}
