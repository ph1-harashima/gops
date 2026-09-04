package com.glv.gsysportal.exception;

/** Phase 9-B: Import Folder placement requires the Excel to already be
 * generated (status GENERATED or later - a retry from FAILED is allowed,
 * see {@code OfficialPoIntegrationRequest#markSubmitted}). Thrown only when
 * the Request is still PENDING. */
public class OfficialPoNotGeneratedException extends RuntimeException {
    public OfficialPoNotGeneratedException(Long orderId) {
        super("Order " + orderId + "'s Official PO Excel has not been generated yet");
    }
}
