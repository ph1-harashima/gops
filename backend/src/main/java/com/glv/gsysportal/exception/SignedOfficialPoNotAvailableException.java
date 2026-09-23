package com.glv.gsysportal.exception;

/** The signed PDF download endpoint requires a signature to have already
 * been registered - mirrors {@link OfficialPoPdfNotGeneratedException}'s
 * own shape. */
public class SignedOfficialPoNotAvailableException extends RuntimeException {
    public SignedOfficialPoNotAvailableException(Long orderId) {
        super("Order " + orderId + " has no signed Official PO PDF registered yet");
    }
}
