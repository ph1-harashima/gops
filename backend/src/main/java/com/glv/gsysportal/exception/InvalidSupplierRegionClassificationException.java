package com.glv.gsysportal.exception;

/** Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章):
 * {@code regionClassification} must be "DOMESTIC" or "OVERSEAS". */
public class InvalidSupplierRegionClassificationException extends RuntimeException {
    public InvalidSupplierRegionClassificationException(String value) {
        super("Region classification must be DOMESTIC or OVERSEAS: " + value);
    }
}
