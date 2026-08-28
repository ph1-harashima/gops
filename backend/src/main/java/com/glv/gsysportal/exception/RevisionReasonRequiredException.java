package com.glv.gsysportal.exception;

/** Phase 7-C5 14章: a correction ("修正版を作成") always requires a reason -
 * mirrors {@link ReturnReasonRequiredException}'s existing pattern. */
public class RevisionReasonRequiredException extends RuntimeException {
    public RevisionReasonRequiredException() {
        super("A reason is required to create a corrected version");
    }
}
