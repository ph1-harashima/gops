package com.glv.gsysportal.exception;

/** Phase 7-C3 4章: Backend-enforced email format check - never relies on
 * Frontend validation alone. */
public class InvalidEmailFormatException extends RuntimeException {
    public InvalidEmailFormatException(String email) {
        super("Invalid email format: " + email);
    }
}
