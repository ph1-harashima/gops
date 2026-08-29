package com.glv.gsysportal.exception;

/** Phase 7-C6 9章: Baseline Capture requires the Legacy PO to actually exist -
 * unlike Compare (7-C6 6章), Capture cannot snapshot a PO that isn't there. */
public class LegacyPoNotFoundForBaselineException extends RuntimeException {
    public LegacyPoNotFoundForBaselineException(String officialPoNo) {
        super("Legacy PO not found for Baseline Capture: " + officialPoNo);
    }
}
