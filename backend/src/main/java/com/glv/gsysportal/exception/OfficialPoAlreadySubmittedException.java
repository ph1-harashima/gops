package com.glv.gsysportal.exception;

/** Phase 9-A: once an Integration Request has reached SUBMITTED (already
 * handed off to the Legacy Import Folder) or CONFIRMED, the Official PO No.
 * and Excel-contract fields (Delivery Week/Date, Ship Via/Term/Payment Term) are
 * locked - editing them now would silently desync Portal from what Legacy
 * already received. */
public class OfficialPoAlreadySubmittedException extends RuntimeException {
    public OfficialPoAlreadySubmittedException(Long orderId, String status) {
        super("Official PO for Order " + orderId + " is already " + status + " - PO No./Excel fields are locked");
    }
}
