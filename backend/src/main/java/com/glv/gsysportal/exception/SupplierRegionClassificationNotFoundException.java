package com.glv.gsysportal.exception;

/** Gap Analysis §12: PUT to an unknown Region Classification Master row id. */
public class SupplierRegionClassificationNotFoundException extends RuntimeException {
    public SupplierRegionClassificationNotFoundException(Long id) {
        super("Region Classification not found: " + id);
    }
}
