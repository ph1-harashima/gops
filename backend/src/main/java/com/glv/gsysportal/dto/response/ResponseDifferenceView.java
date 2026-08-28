package com.glv.gsysportal.dto.response;

/**
 * Phase 7-C5 8章: structured comparison between what an Order Revision asked
 * for and what the Supplier's Response answered, for one line and one
 * dimension. Computed fresh on every read (not persisted) from
 * {@code portal_order_revision_detail} vs {@code supplier_response_detail} -
 * the persisted signal for "this needs review" remains
 * {@link AttentionSummary} (Attention), never this DTO itself.
 */
public record ResponseDifferenceView(
        String type,
        String skuCode,
        String orderedValue,
        String confirmedValue,
        String severity
) {
    public static final String TYPE_QUANTITY_CHANGED = "QUANTITY_CHANGED";
    public static final String TYPE_DELIVERY_CHANGED = "DELIVERY_CHANGED";
    public static final String TYPE_UNANSWERED = "UNANSWERED";

    public static final String SEVERITY_INFO = "INFO";
    public static final String SEVERITY_WARNING = "WARNING";
}
