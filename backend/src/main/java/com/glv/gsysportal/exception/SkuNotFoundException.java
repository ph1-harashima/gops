package com.glv.gsysportal.exception;

import java.util.Set;

/** Thrown when one or more requested SKUs cannot be re-fetched from Legacy. */
public class SkuNotFoundException extends RuntimeException {

    private final Set<String> missingSkus;

    public SkuNotFoundException(Set<String> missingSkus) {
        super("SKU(s) not found in Legacy: " + missingSkus);
        this.missingSkus = missingSkus;
    }

    public Set<String> missingSkus() {
        return missingSkus;
    }
}
