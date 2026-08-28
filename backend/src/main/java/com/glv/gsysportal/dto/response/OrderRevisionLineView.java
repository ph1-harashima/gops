package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderRevisionLineView(
        String skuCode,
        String itemName,
        int recommendedQty,
        int orderedQty,
        LocalDate requestedDelivery,
        BigDecimal unitPrice
) {
}
