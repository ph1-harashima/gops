package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;

/**
 * Order Candidate List row.
 *
 * IMPORTANT: no Japanese display text is included here (Requirements MD 30.12).
 * itemStatus/dataSource are internal English codes; the Frontend resolves them
 * to Japanese via react-i18next resources.
 */
public record OrderCandidateResponse(
        String sku,
        String itemName,
        String brandCode,
        String brandName,
        String supplierCode,
        String supplierName,
        Integer currentStock,
        Integer safetyStock,
        Integer openPo,
        Integer monthlySales,
        String leadTime,
        Integer recommendedQty,
        String itemStatus,
        BigDecimal unitPrice,
        String currency,
        String dataSource
) {
}
