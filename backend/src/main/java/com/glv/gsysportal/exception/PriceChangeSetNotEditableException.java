package com.glv.gsysportal.exception;

/** Thrown when an edit (add/remove Detail, change Proposed Price, change
 * note) is attempted on a Price Change Set whose status is not DRAFT. */
public class PriceChangeSetNotEditableException extends RuntimeException {
    public PriceChangeSetNotEditableException(Long id, String status) {
        super("Price Change Set " + id + " is not editable (status=" + status + ")");
    }
}
