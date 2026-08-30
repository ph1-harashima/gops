package com.glv.gsysportal.dto.response;

/**
 * One PO line's traceability row within Arrival Detail (Phase 8-G 6章's
 * PO->Invoice->Stock-In per-SKU breakdown). {@code invoiceQty} only counts
 * this Arrival's own (Original) Invoice line - never a linked Credit line's
 * qty; {@code stockInQty} nets Original + linked Credit qty_stk_in together
 * - the exact same distinction {@code FulfillmentLineView} already
 * establishes (Phase 7-C7A), reused here rather than re-derived. No line
 * Status/outstanding field - Phase 8-G 7章 forbids it for this screen even
 * though Fulfillment computes one for its own, different purpose.
 */
public record ArrivalLineView(
        String sku,
        String itemName,
        Integer orderedQty,
        Integer invoiceQty,
        Integer stockInQty) {
}
