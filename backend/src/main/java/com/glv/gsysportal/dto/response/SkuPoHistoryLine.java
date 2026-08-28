package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One actual Legacy PO line for this SKU (implementation instructions
 * Step 5 4章 - never a fabricated/estimated history entry). */
public record SkuPoHistoryLine(
        String poNo,
        LocalDate orderDate,
        String status,
        Integer qty,
        BigDecimal unitPrice,
        String currency,
        String supplierCode,
        String supplierName
) {
}
