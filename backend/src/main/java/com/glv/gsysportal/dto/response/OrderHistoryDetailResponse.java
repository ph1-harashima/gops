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
        List<String> orderAttentionTypes
) {
}
