package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record OrderDraftDetailResponse(
        Long id,
        String sku,
        String itemName,
        Integer recommendedQty,
        Integer orderQty,
        BigDecimal unitPrice,
        BigDecimal amount,
        Integer currentStock,
        Integer safetyStock,
        Integer openPo,
        Integer monthlySales,
        String leadTime,
        String itemStatus,
        String dataSource,
        List<String> warningCodes
) {
}
