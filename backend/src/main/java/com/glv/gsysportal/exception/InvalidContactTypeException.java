package com.glv.gsysportal.exception;

/** Phase 7-C3 2章: only TO/CC are allowed. */
public class InvalidContactTypeException extends RuntimeException {
    public InvalidContactTypeException(String contactType) {
        super("Invalid contact type (only TO/CC allowed): " + contactType);
    }
}
