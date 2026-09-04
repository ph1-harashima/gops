package com.glv.gsysportal.exception;

/** Phase 9-E: the (currently only) Attachment type is the Official PO
 * Excel - Send requires it to already be generated (design doc §5's
 * default attachment behavior). */
public class EmailAttachmentNotReadyException extends RuntimeException {
    public EmailAttachmentNotReadyException(Long orderId) {
        super("Order " + orderId + " has no generated Official PO Excel to attach yet");
    }
}
