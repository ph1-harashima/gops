package com.glv.gsysportal.exception;

public class PriceChangeSetDetailNotFoundException extends RuntimeException {
    public PriceChangeSetDetailNotFoundException(Long detailId) {
        super("Price Change Set Detail not found: " + detailId);
    }
}
