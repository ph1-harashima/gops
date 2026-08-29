package com.glv.gsysportal.exception;

/** Phase 7-C6 13章: {@code intent} must be "NEW" or "UPDATE". */
public class InvalidIntegrationIntentException extends RuntimeException {
    public InvalidIntegrationIntentException(String intent) {
        super("Invalid Integration Intent: " + intent);
    }
}
