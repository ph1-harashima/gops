package com.glv.gsysportal.exception;

/** Phase 9-C: G-SYS Import Confirmation requires the Excel to already have
 * been placed into the Import Folder (status SUBMITTED or later) - checking
 * Legacy before anything was ever sent there provides no value. */
public class OfficialPoNotSubmittedException extends RuntimeException {
    public OfficialPoNotSubmittedException(Long orderId) {
        super("Order " + orderId + " has not been submitted to the Import Folder yet");
    }
}
