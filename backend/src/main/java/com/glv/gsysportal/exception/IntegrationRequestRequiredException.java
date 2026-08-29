package com.glv.gsysportal.exception;

/** Phase 7-C6 9章: Baseline Capture requires an existing Integration Request
 * for the Order's current target Revision ("G-SYS連携準備" must run first). */
public class IntegrationRequestRequiredException extends RuntimeException {
    public IntegrationRequestRequiredException(Long orderId, int targetRevisionNo) {
        super("No Official PO Integration Request exists for Order " + orderId + " Revision " + targetRevisionNo);
    }
}
