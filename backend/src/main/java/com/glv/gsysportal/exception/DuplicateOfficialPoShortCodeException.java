package com.glv.gsysportal.exception;

/** BR-08: (codeType, businessCode) must be unique among ACTIVE rows -
 * mirrors {@code DuplicateSupplierRegionClassificationException}'s own
 * Uniqueness policy. */
public class DuplicateOfficialPoShortCodeException extends RuntimeException {
    public DuplicateOfficialPoShortCodeException(String codeType, String businessCode) {
        super("An active Short Code already exists for " + codeType + " " + businessCode);
    }
}
