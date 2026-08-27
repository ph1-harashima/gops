package com.glv.gsysportal.exception;

public class EmptySkuListException extends RuntimeException {
    public EmptySkuListException() {
        super("At least one SKU is required");
    }
}
