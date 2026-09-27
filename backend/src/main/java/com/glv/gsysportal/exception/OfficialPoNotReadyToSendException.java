package com.glv.gsysportal.exception;

/** Business Rule (confirmed): only a signed/stamped Official PO PDF may be
 * sent to the Supplier - never a fallback to the unsigned/generated PDF.
 * Send Guard: reuses {@code OfficialPoIntegrationRequest#isReadyToSend()}
 * as the single source of truth (SIGNATURE_SIGNED + LIFECYCLE_ACTIVE) -
 * this exception never re-derives that condition itself, it only reports
 * which of the two axes was not satisfied for the UI to display. */
public class OfficialPoNotReadyToSendException extends RuntimeException {
    public OfficialPoNotReadyToSendException(Long orderId, String signatureStatus, String lifecycleStatus) {
        super("Order " + orderId + " is not ready to send (signatureStatus=" + signatureStatus
                + ", lifecycleStatus=" + lifecycleStatus + ") - isReadyToSend() requires SIGNED + ACTIVE");
    }
}
