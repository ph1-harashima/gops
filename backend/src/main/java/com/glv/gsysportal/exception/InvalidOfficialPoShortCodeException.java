package com.glv.gsysportal.exception;

/** BR-08: {@code codeType} must be SUPPLIER/BRAND and {@code shortCode} must
 * be exactly 3 characters (Postgres CHECK constraint mirrored here so the
 * error is a clean 400, not a raw constraint-violation 500). */
public class InvalidOfficialPoShortCodeException extends RuntimeException {
    public InvalidOfficialPoShortCodeException(String message) {
        super(message);
    }
}
