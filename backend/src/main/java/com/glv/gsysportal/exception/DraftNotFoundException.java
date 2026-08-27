package com.glv.gsysportal.exception;

public class DraftNotFoundException extends RuntimeException {
    public DraftNotFoundException(Long id) {
        super("Draft not found: " + id);
    }
}
