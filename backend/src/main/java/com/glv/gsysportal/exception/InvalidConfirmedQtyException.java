package com.glv.gsysportal.exception;

/** Save Supplier Response (implementation instructions 11章): confirmedQty,
 * when provided (non-null), must be an integer &gt;= 0. NULL itself is
 * always valid (means "not yet answered") and never reaches this check. */
public class InvalidConfirmedQtyException extends RuntimeException {
    public InvalidConfirmedQtyException(Integer value) {
        super("Confirmed Qty must be >= 0, got: " + value);
    }
}
