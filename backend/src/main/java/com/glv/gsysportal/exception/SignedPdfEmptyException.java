package com.glv.gsysportal.exception;

/** Signed PDF Upload Security audit: an empty upload is never a valid
 * signed PDF. */
public class SignedPdfEmptyException extends RuntimeException {
    public SignedPdfEmptyException(Long orderId) {
        super("Order " + orderId + "'s signed PDF upload was empty");
    }
}
