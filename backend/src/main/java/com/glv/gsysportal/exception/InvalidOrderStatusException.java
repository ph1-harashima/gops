package com.glv.gsysportal.exception;

/** PO Preview (implementation instructions 3章): the Order's current Status
 * does not support Preview at all (e.g. an already-Sent order). Distinct
 * from {@link InvalidStatusTransitionException}, which guards a specific
 * state-machine transition (Confirm/Return to Draft) rather than a
 * read-only view. */
public class InvalidOrderStatusException extends RuntimeException {
    public InvalidOrderStatusException(String actualStatus) {
        super("Order Status does not support this operation: " + actualStatus);
    }
}
