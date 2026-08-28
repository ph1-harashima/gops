package com.glv.gsysportal.dto.response;

import java.time.LocalDate;
import java.util.List;

/** The Recommended -> Ordered -> Confirmed 3-stage line (implementation
 * instructions 25章). {@code confirmedQty} is null whenever no Supplier
 * Response line exists yet for this Order (e.g. still DRAFT/READY_TO_ORDER)
 * or the line has not been answered - the same null/0 distinction as
 * elsewhere in Step 4. */
public record OrderHistoryDetailLineView(
        String sku,
        String itemName,
        int recommendedQty,
        int orderedQty,
        Integer confirmedQty,
        LocalDate requestedDelivery,
        LocalDate confirmedDelivery,
        List<AttentionSummary> attentions
) {
}
