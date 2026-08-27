package com.glv.gsysportal.exception;

/** Confirm Supplier Response (implementation instructions 20章):
 * [PROTOTYPE DECISION] the provisional completion condition is "every
 * non-removed line has a non-null confirmedQty" - Confirmed Delivery is NOT
 * required. The formal completion condition remains
 * [TBD - CUSTOMER REVIEW]. */
public class SupplierResponseIncompleteException extends RuntimeException {
    public SupplierResponseIncompleteException(Long orderId) {
        super("Supplier Response is incomplete for Order: " + orderId);
    }
}
