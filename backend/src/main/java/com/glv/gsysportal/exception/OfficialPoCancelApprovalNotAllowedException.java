package com.glv.gsysportal.exception;

/** BR-03 (docs/gulliver-20260917-confirmed-business-rules.md): only a
 * Document currently in CANCEL_REQUESTED can be Cancel-Approved - there
 * must be an actual pending Request first (never a direct ACTIVE ->
 * CANCELLED shortcut). */
public class OfficialPoCancelApprovalNotAllowedException extends RuntimeException {
    public OfficialPoCancelApprovalNotAllowedException(Long orderId, String lifecycleStatus) {
        super("Order " + orderId + "'s current Official PO Document is " + lifecycleStatus
                + ", not CANCEL_REQUESTED - nothing pending to approve");
    }
}
