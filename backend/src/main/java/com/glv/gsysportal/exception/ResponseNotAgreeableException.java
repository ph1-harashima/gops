package com.glv.gsysportal.exception;

/** Phase 7-C5 11章: Agreement may only be performed for an Order that is
 * currently SUPPLIER_CONFIRMED, and only for the CURRENT Response (the one
 * for the Order's currently-sent Revision) - never a past, superseded one. */
public class ResponseNotAgreeableException extends RuntimeException {
    public ResponseNotAgreeableException(Long orderId, Long responseId) {
        super("Response " + responseId + " on Order " + orderId + " is not agreeable in the current state");
    }
}
