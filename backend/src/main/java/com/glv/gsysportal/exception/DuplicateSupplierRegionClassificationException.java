package com.glv.gsysportal.exception;

/** Gap Analysis §12: (supplierCode, brandCode) must be unique among ACTIVE
 * rows - mirrors {@code DuplicateManufacturerChannelException}'s own
 * Uniqueness policy. */
public class DuplicateSupplierRegionClassificationException extends RuntimeException {
    public DuplicateSupplierRegionClassificationException(String supplierCode, String brandCode) {
        super("An active Region Classification already exists for supplier=" + supplierCode + " brand=" + brandCode);
    }
}
