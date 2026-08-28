package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/items/{sku}/ordering-context (implementation instructions
 * Step 5 4章). Reuses the same Legacy Read Query as the Order Candidate
 * List (Technical Design 4.2/8章) - no new Legacy Read surface is added for
 * the Product/Inventory/Ordering sections, only the History section adds a
 * dedicated PO history query.
 *
 * 重要 (implementation instructions Step 5 4章): no Sales Trend, no
 * 直近30/60/90日 or 前月/前々月 breakdown exists anywhere in this DTO - Legacy
 * only exposes a single current-month monthlySales figure
 * (Phase 0.5 audit finding), and fabricating a trend from one data point
 * is explicitly prohibited.
 */
public record SkuDetailResponse(
        // Product
        String sku,
        String itemName,
        String brandCode,
        String brandName,
        String supplierCode,
        String supplierName,
        String itemStatus,
        // Inventory
        Integer currentStock,
        Integer safetyStock,
        Integer openPo,
        Integer openArrival,
        // Ordering
        Integer monthlySales,
        String leadTime,
        Integer recommendedQty,
        BigDecimal unitPrice,
        String currency,
        String dataSource,
        // History
        List<SkuPoHistoryLine> poHistory
) {
}
