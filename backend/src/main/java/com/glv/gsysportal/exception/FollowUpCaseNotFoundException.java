package com.glv.gsysportal.exception;

public class FollowUpCaseNotFoundException extends RuntimeException {
    public FollowUpCaseNotFoundException(Long id) {
        super("Follow-up Case not found: " + id);
    }
}
