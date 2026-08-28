package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** GET/PUT /api/orders/{id}/supplier-response (implementation instructions
 * 9章). */
public record SupplierResponseView(
        Long orderId,
        String draftNo,
        String prototypePoNo,
        String supplierCode,
        String supplierName,
        String brandCode,
        String brandName,
        LocalDate orderDate,
        String status,
        int totalOrderedQty,
        BigDecimal totalAmount,
        LocalDate responseDate,
        String responseNote,
        String responseStatus,
        List<SupplierResponseDetailView> details,
        List<AttentionSummary> orderAttentions,
        SupplierResponseSummary summary
) {
}
