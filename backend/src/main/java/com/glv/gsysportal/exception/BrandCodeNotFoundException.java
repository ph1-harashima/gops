package com.glv.gsysportal.exception;

/** Phase 7-C3 14章: same Legacy READ ONLY existence check as
 * {@link SupplierCodeNotFoundException}, for the optional Brand Code. */
public class BrandCodeNotFoundException extends RuntimeException {
    public BrandCodeNotFoundException(String brandCode) {
        super("Brand code not found in Legacy: " + brandCode);
    }
}
