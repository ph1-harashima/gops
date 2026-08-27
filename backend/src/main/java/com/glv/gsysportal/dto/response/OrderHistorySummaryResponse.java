package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/orders/history row (implementation instructions 24章). */
public record OrderHistorySummaryResponse(
        Long id,
        String draftNo,
        String prototypePoNo,
        LocalDate orderDate,
        String supplierCode,
        String supplierName,
        String brandCode,
        String brandName,
        int skuCount,
        int totalOrderedQty,
        BigDecimal totalAmount,
        String status,
        List<String> activeAttentionTypes,
        OffsetDateTime updatedAt
) {
}
