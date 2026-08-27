package com.glv.gsysportal.exception;

/** PO Preview / Confirm Order Validation (implementation instructions 3章):
 * at least one non-removed line with orderQty &gt; 0 is required. */
public class NoOrderableItemsException extends RuntimeException {
    public NoOrderableItemsException(Long draftId) {
        super("No orderable items (order_qty > 0) in Draft: " + draftId);
    }
}
