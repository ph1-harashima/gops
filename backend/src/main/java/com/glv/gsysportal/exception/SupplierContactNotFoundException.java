package com.glv.gsysportal.exception;

public class SupplierContactNotFoundException extends RuntimeException {
    public SupplierContactNotFoundException(Long id) {
        super("Supplier Contact not found: " + id);
    }
}
