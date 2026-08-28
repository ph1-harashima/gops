package com.glv.gsysportal.repository.legacy.row;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One TR_PO/TR_PO_DTL line for a given SKU (implementation instructions
 * Step 5 4章 SKU Detail History - "取得可能なLegacyPO履歴のみ"). */
public record LegacyPoHistoryRow(
        String poNo,
        LocalDate orderDate,
        String status,
        Integer qty,
        BigDecimal unitPrice,
        String currency,
        String supplierCd,
        String supplierName
) {
}
