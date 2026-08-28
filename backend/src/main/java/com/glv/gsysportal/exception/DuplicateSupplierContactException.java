package com.glv.gsysportal.exception;

/** Phase 7-C3 4章's Uniqueness policy: (supplierCode, brandCode, email)
 * must be unique among ACTIVE rows - re-adding a previously-deactivated
 * contact with the same email is allowed (the old row stays inactive). */
public class DuplicateSupplierContactException extends RuntimeException {
    public DuplicateSupplierContactException(String supplierCode, String brandCode, String email) {
        super("An active Supplier Contact already exists for supplier=" + supplierCode
                + " brand=" + brandCode + " email=" + email);
    }
}
