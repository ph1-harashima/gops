package com.glv.gsysportal.exception;

/** Phase 9-E: real Email Send requires the Manufacturer Channel Master to
 * resolve EMAIL for this Order's supplier[+brand] - never shown/callable
 * for an EDI-channel or unresolved manufacturer (Production PO Workflow
 * §6's explicit "同じSend Emailボタンを出さないこと"). */
public class EmailChannelNotApplicableException extends RuntimeException {
    public EmailChannelNotApplicableException(Long orderId, String resolvedChannel) {
        super("Order " + orderId + "'s resolved Manufacturer Channel is not EMAIL (actual: " + resolvedChannel + ")");
    }
}
