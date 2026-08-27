package com.glv.gsysportal.exception;

/** Save Draft (implementation instructions 16章): PUT /api/orders/drafts/{id}
 * is only permitted while Status = DRAFT. Once READY_TO_ORDER, the Order
 * must be returned to DRAFT (POST /api/orders/{id}/return-to-draft) before
 * it can be edited again. Maps to HTTP 409. */
public class OrderNotEditableException extends RuntimeException {
    public OrderNotEditableException(String currentStatus) {
        super("Order is not editable in status: " + currentStatus);
    }
}
