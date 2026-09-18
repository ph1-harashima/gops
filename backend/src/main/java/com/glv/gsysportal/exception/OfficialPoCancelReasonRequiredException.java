package com.glv.gsysportal.exception;

/** Gap Analysis C-4 (docs/gulliver-20260917-phase1-gap-analysis.md 9章):
 * Cancel always requires an explicit reason (Audit Trail: 誰が・いつ・何を・
 * なぜ) - mirrors {@code RevisionReasonRequiredException}'s own shape. */
public class OfficialPoCancelReasonRequiredException extends RuntimeException {
    public OfficialPoCancelReasonRequiredException() {
        super("A reason is required to cancel an Official PO Document");
    }
}
