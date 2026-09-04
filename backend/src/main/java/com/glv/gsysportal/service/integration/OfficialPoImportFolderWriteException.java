package com.glv.gsysportal.service.integration;

/** Phase 9-B: an {@link OfficialPoImportFolderAdapter} failed to place the
 * File. Never propagated raw to the API - {@code OfficialPoIntegrationService}
 * catches this and marks the Integration Request FAILED (design doc §10). */
public class OfficialPoImportFolderWriteException extends RuntimeException {
    public OfficialPoImportFolderWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
