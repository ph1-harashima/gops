package com.glv.gsysportal.dto.response;

import java.util.List;

/**
 * GET /api/orders/{id}/fulfillment (Phase 7-C7A 4章/6章). {@code linkState}:
 * <ul>
 *   <li>{@code NOT_LINKED} - {@code portal_order.official_po_no} is still
 *       NULL (7-C2B not implemented) - the normal Demo Order state. Never
 *       shown as "0件" or "未納" (7-C7A 4章's explicit instruction) - the
 *       Frontend renders an explicit "G-SYS正式PO未連携" notice instead.</li>
 *   <li>{@code PO_NOT_FOUND} - officialPoNo is set (Test-only per 5章) but no
 *       matching TR_PO exists in Legacy - a defensive state, not expected in
 *       normal operation.</li>
 *   <li>{@code LINKED} - a real TR_PO was found; {@code fulfillmentStatus}
 *       and {@code lines} are populated.</li>
 * </ul>
 * {@code fulfillmentStatus}/{@code lines} are null/empty unless {@code linkState == LINKED}.
 */
public record FulfillmentView(
        Long orderId,
        String officialPoNo,
        String linkState,
        String fulfillmentStatus,
        List<FulfillmentLineView> lines
) {
    public static final String LINK_STATE_NOT_LINKED = "NOT_LINKED";
    public static final String LINK_STATE_PO_NOT_FOUND = "PO_NOT_FOUND";
    public static final String LINK_STATE_LINKED = "LINKED";
}
