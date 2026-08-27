package com.glv.gsysportal.exception;

import java.util.Set;

/**
 * Thrown when Create Draft is requested for SKUs spanning more than one
 * Supplier (implementation instructions 5章 - no auto-split this Step,
 * simply reject with 400 MIXED_SUPPLIER_NOT_ALLOWED).
 */
public class MixedSupplierException extends RuntimeException {

    private final Set<String> supplierCodes;

    public MixedSupplierException(Set<String> supplierCodes) {
        super("Multiple suppliers in one Draft is not allowed: " + supplierCodes);
        this.supplierCodes = supplierCodes;
    }

    public Set<String> supplierCodes() {
        return supplierCodes;
    }
}
