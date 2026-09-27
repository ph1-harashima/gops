package com.glv.gsysportal.exception;

/** Signed PDF Upload Security audit: the uploaded file exceeds
 * app.official-po.signed-pdf.max-size-bytes (see application.yml for the
 * chosen value and rationale - no existing system requirement specified
 * one, so this is a newly config'd general-purpose ceiling). */
public class SignedPdfTooLargeException extends RuntimeException {
    public SignedPdfTooLargeException(Long orderId, long actualBytes, long maxBytes) {
        super("Order " + orderId + "'s signed PDF upload is " + actualBytes + " bytes, exceeding the " + maxBytes + "-byte limit");
    }
}
