package com.glv.gsysportal.exception;

/** Defensive consistency guard (Business Rule audit): should be
 * unreachable in practice - {@code OfficialPoIntegrationRequest#markSigned}
 * always sets signedPdfFileKey in the same transition that sets
 * signatureStatus=SIGNED, so isReadyToSend()==true should always imply a
 * signed file key is present. Guards against exactly the "SIGNED but no
 * signedPdfFileKey" inconsistency explicitly called out as unacceptable -
 * Send must never silently fall back to the unsigned PDF in that case. */
public class SignedPdfMissingException extends RuntimeException {
    public SignedPdfMissingException(Long orderId) {
        super("Order " + orderId + " is marked SIGNED but has no signed PDF file key on record - refusing to send");
    }
}
