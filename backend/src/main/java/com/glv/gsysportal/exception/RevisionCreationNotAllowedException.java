package com.glv.gsysportal.exception;

/** Phase 7-C5 13章: "修正版を作成" (create a corrected version) may only be
 * performed for an Order that is currently SUPPLIER_CONFIRMED. */
public class RevisionCreationNotAllowedException extends RuntimeException {
    public RevisionCreationNotAllowedException(Long orderId, String actualStatus) {
        super("Order " + orderId + " is not SUPPLIER_CONFIRMED (current status: " + actualStatus + ") - cannot create a corrected version");
    }
}
