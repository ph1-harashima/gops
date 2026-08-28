package com.glv.gsysportal.exception;

/** Phase 7-C3 14章: Supplier Contact/Mail Template supplierCode must exist in
 * the Legacy Supplier Master (READ ONLY check) - a nonexistent Code can
 * never be registered. */
public class SupplierCodeNotFoundException extends RuntimeException {
    public SupplierCodeNotFoundException(String supplierCode) {
        super("Supplier code not found in Legacy: " + supplierCode);
    }
}
