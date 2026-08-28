package com.glv.gsysportal.exception;

public class AttentionNotFoundException extends RuntimeException {
    public AttentionNotFoundException(Long id) {
        super("Attention not found: " + id);
    }
}
