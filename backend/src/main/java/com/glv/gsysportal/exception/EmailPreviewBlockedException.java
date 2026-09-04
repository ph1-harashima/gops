package com.glv.gsysportal.exception;

/** Phase 9-E: Send reuses MailPreviewService's own resolution - if Preview
 * would be BLOCKED (missing Contact/Template/PO No.), Send refuses too. Run
 * Mail Preview to see exactly which issue is blocking. */
public class EmailPreviewBlockedException extends RuntimeException {
    public EmailPreviewBlockedException(Long orderId) {
        super("Order " + orderId + "'s Mail Preview is BLOCKED - resolve the issues shown there first");
    }
}
