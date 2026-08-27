package com.glv.gsysportal.exception;

import java.util.Set;

/** PO Preview / Confirm Order Validation (implementation instructions 3章):
 * a line with orderQty &gt; 0 has no Unit Price. Never fabricate a price -
 * this blocks Preview/Confirm instead. */
public class MissingUnitPriceException extends RuntimeException {

    private final Set<String> skus;

    public MissingUnitPriceException(Set<String> skus) {
        super("Unit Price missing for SKU(s): " + skus);
        this.skus = skus;
    }

    public Set<String> skus() {
        return skus;
    }
}
