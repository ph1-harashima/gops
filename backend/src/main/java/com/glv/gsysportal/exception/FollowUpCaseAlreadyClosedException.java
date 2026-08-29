package com.glv.gsysportal.exception;

/** Phase 7-C7A 10章: a CLOSED Case is done - no further edit, close, or
 * Mail Preview is possible against it. */
public class FollowUpCaseAlreadyClosedException extends RuntimeException {
    public FollowUpCaseAlreadyClosedException(Long id) {
        super("Follow-up Case is already CLOSED: " + id);
    }
}
