package com.glv.gsysportal.dto.response;

/**
 * Phase 7-C7A 3章/8章. {@code outstandingQty} is the raw signed difference
 * (orderedQty - stockInQty, already netted against any Credit PO adjustment,
 * see FulfillmentReadRepository's Javadoc) - never clamped to zero, so an
 * over-receipt is visible as a negative Outstanding rather than hidden.
 * {@code lineStatus} is one of OPEN/PARTIAL/FULFILLED (7-C7A 1章/3章).
 */
public record FulfillmentLineView(
        String skuCode,
        String itemName,
        int orderedQty,
        int invoicedQty,
        int stockInQty,
        int outstandingQty,
        String lineStatus
) {
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FULFILLED = "FULFILLED";
}
