package com.glv.gsysportal.exception;

/** Phase 9-A: Excel generation requires a confirmed Official PO No. first
 * (docs/official-po-integration-detailed-design.md §7's Gate - Excel's PO
 * No. cell must never be a Portal-internal placeholder). */
public class OfficialPoNumberRequiredException extends RuntimeException {
    public OfficialPoNumberRequiredException(Long orderId) {
        super("Order " + orderId + " has no confirmed Official PO No. yet");
    }
}
