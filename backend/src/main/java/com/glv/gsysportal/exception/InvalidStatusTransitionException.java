package com.glv.gsysportal.exception;

/** Confirm Order / Return to Draft (implementation instructions 9章/13章):
 * the requested Status transition is not allowed from the Order's current
 * Status. Confirm Order requires exactly DRAFT (so a duplicate/re-sent
 * Confirm from an already-READY_TO_ORDER order is rejected outright, before
 * any PO No. is (re-)generated or any Audit row is written - implementation
 * instructions 9章 idempotency). Return to Draft requires exactly
 * READY_TO_ORDER. Maps to HTTP 409. */
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String currentStatus, String requiredStatus) {
        super("Invalid status transition: current=" + currentStatus + ", required=" + requiredStatus);
    }
}
