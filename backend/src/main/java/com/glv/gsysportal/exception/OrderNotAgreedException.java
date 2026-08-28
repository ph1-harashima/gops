package com.glv.gsysportal.exception;

/** Phase 7-C5 18章: Reopen Agreement may only be performed for an Order that
 * is currently AGREED. */
public class OrderNotAgreedException extends RuntimeException {
    public OrderNotAgreedException(Long orderId, String actualStatus) {
        super("Order " + orderId + " is not AGREED (current status: " + actualStatus + ")");
    }
}
