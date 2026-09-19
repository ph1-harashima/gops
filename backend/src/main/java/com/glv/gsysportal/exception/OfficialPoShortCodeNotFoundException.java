package com.glv.gsysportal.exception;

public class OfficialPoShortCodeNotFoundException extends RuntimeException {
    public OfficialPoShortCodeNotFoundException(Long id) {
        super("Official PO Short Code not found: " + id);
    }
}
