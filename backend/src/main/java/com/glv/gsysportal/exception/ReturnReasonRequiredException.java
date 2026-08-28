package com.glv.gsysportal.exception;

/** Phase 7-C1 12章: return-for-correction requires a non-blank reason. */
public class ReturnReasonRequiredException extends RuntimeException {
    public ReturnReasonRequiredException() {
        super("Return reason is required");
    }
}
