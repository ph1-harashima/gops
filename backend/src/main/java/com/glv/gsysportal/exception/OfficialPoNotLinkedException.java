package com.glv.gsysportal.exception;

/** Phase 7-C6 9章: Baseline Capture requires {@code officialPoNo != null}. */
public class OfficialPoNotLinkedException extends RuntimeException {
    public OfficialPoNotLinkedException(Long orderId) {
        super("Order " + orderId + " has no Official PO No. linked - cannot capture a Legacy Baseline");
    }
}
