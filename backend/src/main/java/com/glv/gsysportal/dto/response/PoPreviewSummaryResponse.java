package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;

/** Always recomputed server-side from the orderable (orderQty > 0) lines
 * actually included in this Preview - never trusted from portal_order's
 * stored total_qty/total_amount, which cover ALL lines including
 * orderQty = 0 ones (implementation instructions 17章). */
public record PoPreviewSummaryResponse(
        int skuCount,
        int totalQty,
        BigDecimal totalAmount
) {
}
