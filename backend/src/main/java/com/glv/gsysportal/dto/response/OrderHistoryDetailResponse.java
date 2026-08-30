package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** GET /api/orders/{id} (implementation instructions 25章). READ ONLY - no
 * write endpoint for this view; edits go through the Draft/Supplier
 * Response screens. */
public record OrderHistoryDetailResponse(
        Long id,
        String draftNo,
        String prototypePoNo,
        String supplierCode,
        String supplierName,
        String brandCode,
        String brandName,
        LocalDate orderDate,
        LocalDate requestedDelivery,
        String currency,
        String remark,
        String status,
        int totalQty,
        BigDecimal totalAmount,
        List<OrderHistoryDetailLineView> details,
        List<AttentionSummary> orderAttentions,
        // Phase 7-H (EDI発注Workflow Foundation): null until the first Send
        // (either Channel) - see PortalOrder.communicationChannel's Javadoc.
        // Shown here (not on PO Preview, which is unreachable once an Order
        // has moved past APPROVED - PoPreviewService's own existing Status
        // Gate) since Order Detail is the one screen reachable for every
        // Status, Send included.
        String communicationChannel
) {
}
