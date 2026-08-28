package com.glv.gsysportal.exception;

/** Phase 7-C5 9章/11章: Agreement requires every ACTIVE line/order-level
 * Attention to have been acknowledged first - reviewing a difference
 * (Attention acknowledge) is never conflated with reaching Business Agreement,
 * but Agreement itself must not skip past an unreviewed difference. An ADMIN
 * may still override via {@code forceAgree} (7-C5 11章's "ADMIN明示的に合意"
 * candidate). */
public class UnacknowledgedAttentionException extends RuntimeException {
    public UnacknowledgedAttentionException(Long orderId) {
        super("Order " + orderId + " has unacknowledged Attention(s) - acknowledge them first or pass forceAgree=true");
    }
}
