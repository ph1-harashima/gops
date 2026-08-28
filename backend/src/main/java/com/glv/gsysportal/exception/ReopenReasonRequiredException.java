package com.glv.gsysportal.exception;

/** Phase 7-C5 18章: Reopen Agreement always requires a reason - mirrors
 * {@link ReturnReasonRequiredException}'s existing pattern. */
public class ReopenReasonRequiredException extends RuntimeException {
    public ReopenReasonRequiredException() {
        super("A reason is required to reopen an Agreement");
    }
}
