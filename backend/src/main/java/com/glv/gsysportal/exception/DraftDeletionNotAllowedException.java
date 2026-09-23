package com.glv.gsysportal.exception;

/** G-OPS Operational Workflow Realignment Phase F §16: a Draft can only be
 * deleted while status=DRAFT AND no downstream process has started yet
 * (no Official PO Integration Request ever created for it, and it has
 * never actually been sent - no Revision history exists). */
public class DraftDeletionNotAllowedException extends RuntimeException {
    public DraftDeletionNotAllowedException(Long orderId, String reason) {
        super("Draft " + orderId + " cannot be deleted: " + reason);
    }
}
