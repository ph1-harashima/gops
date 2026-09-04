package com.glv.gsysportal.service.integration;

/** Phase 9-E: an {@link EmailSenderPort} failed to deliver the Email. Never
 * propagated raw to the API - {@code EmailSendService} catches this and
 * marks the {@code order_email} row FAILED. */
public class EmailSendException extends RuntimeException {
    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
