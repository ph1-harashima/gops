package com.glv.gsysportal.exception;

/** Phase 7-C7A 11章: the official Reason classification remains CUSTOMER
 * REVIEW, but the candidate 5-value allow-list itself is enforced. */
public class InvalidFollowUpReasonException extends RuntimeException {
    public InvalidFollowUpReasonException(String reason) {
        super("Invalid Follow-up Case reason: " + reason);
    }
}
