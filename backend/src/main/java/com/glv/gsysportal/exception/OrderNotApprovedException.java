package com.glv.gsysportal.exception;

/** Phase 7-C2A 6章: Official PO Integration Request may only be created for
 * an APPROVED Order. */
public class OrderNotApprovedException extends RuntimeException {
    public OrderNotApprovedException(Long orderId, String actualStatus) {
        super("Order " + orderId + " is not APPROVED (current status: " + actualStatus + ")");
    }
}
