package com.glv.gsysportal.exception;

/** Gap Analysis C-4 (docs/gulliver-20260917-phase1-gap-analysis.md 9章):
 * only the current ACTIVE Document can be cancelled - a SUPERSEDED or
 * already-CANCELLED Document is a terminal historical record. */
public class OfficialPoCancelNotAllowedException extends RuntimeException {
    public OfficialPoCancelNotAllowedException(Long orderId, String lifecycleStatus) {
        super("Order " + orderId + "'s current Official PO Document is " + lifecycleStatus + ", not ACTIVE - cannot cancel");
    }
}
