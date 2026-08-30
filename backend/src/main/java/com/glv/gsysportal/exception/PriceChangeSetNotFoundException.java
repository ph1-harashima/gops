package com.glv.gsysportal.exception;

public class PriceChangeSetNotFoundException extends RuntimeException {
    public PriceChangeSetNotFoundException(Long id) {
        super("Price Change Set not found: " + id);
    }
}
