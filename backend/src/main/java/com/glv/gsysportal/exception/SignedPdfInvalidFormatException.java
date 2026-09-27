package com.glv.gsysportal.exception;

/** Signed PDF Upload Security audit: the uploaded content's own magic
 * bytes ("%PDF-") do not identify it as a PDF - the client-supplied
 * Content-Type header is never trusted alone (easily spoofed), so this is
 * always a byte-content check, never a header-only check. */
public class SignedPdfInvalidFormatException extends RuntimeException {
    public SignedPdfInvalidFormatException(Long orderId) {
        super("Order " + orderId + "'s signed PDF upload does not have a valid PDF signature (%PDF-)");
    }
}
