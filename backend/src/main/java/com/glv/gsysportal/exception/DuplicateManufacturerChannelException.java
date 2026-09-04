package com.glv.gsysportal.exception;

/** Phase 9-D: (supplierCode, brandCode) must be unique among ACTIVE rows -
 * mirrors {@code DuplicateSupplierContactException}'s own Uniqueness policy. */
public class DuplicateManufacturerChannelException extends RuntimeException {
    public DuplicateManufacturerChannelException(String supplierCode, String brandCode) {
        super("An active Manufacturer Channel already exists for supplier=" + supplierCode + " brand=" + brandCode);
    }
}
