package com.glv.gsysportal.exception;

/** Phase 9-D: {@code channel} must be "EMAIL" or "EDI". */
public class InvalidManufacturerChannelException extends RuntimeException {
    public InvalidManufacturerChannelException(String channel) {
        super("Channel must be EMAIL or EDI: " + channel);
    }
}
