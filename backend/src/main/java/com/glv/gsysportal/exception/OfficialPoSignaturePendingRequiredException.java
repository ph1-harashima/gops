package com.glv.gsysportal.exception;

/** G-OPS Operational Workflow Realignment Phase C: a signed PDF can only be
 * registered against a formal PDF that is currently PENDING signature -
 * there must be an unsigned PDF to sign in the first place (NOT_REQUIRED),
 * and an already-SIGNED Request must not be silently re-signed (a new PDF
 * generation resets it to PENDING first, per {@code
 * OfficialPoIntegrationRequest#resetSignatureForNewPdf}). */
public class OfficialPoSignaturePendingRequiredException extends RuntimeException {
    public OfficialPoSignaturePendingRequiredException(Long orderId, String actualSignatureStatus) {
        super("Order " + orderId + " is not awaiting a signature (signatureStatus=" + actualSignatureStatus + ")");
    }
}
