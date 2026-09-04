package com.glv.gsysportal.exception;

/** Phase 9-D: "EDI入力完了" only applies to an Order whose communicationChannel
 * is EDI (i.e. it went through {@code recordEdiSend}) - completing EDI input
 * on an EMAIL-channel (or not-yet-sent) Order has no meaning. */
public class EdiCompletionNotApplicableException extends RuntimeException {
    public EdiCompletionNotApplicableException(Long orderId, String actualChannel) {
        super("Order " + orderId + "'s communication channel is not EDI (actual: " + actualChannel + ")");
    }
}
