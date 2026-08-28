package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** GET/PUT /api/orders/{id}/supplier-response (implementation instructions
 * 9章), extended Phase 7-C5 21章 to be Revision-aware: {@code responseId}/
 * {@code revisionNo} identify exactly which (Order, Revision) this View is
 * for, and {@code differences}/agreement fields surface the 7-C5 8章/11章
 * Difference Detection and Agreement state. */
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
        SupplierResponseSummary summary,
        Long responseId,
        int revisionNo,
        boolean isCurrent,
        List<ResponseDifferenceView> differences,
        String agreedBy,
        java.time.OffsetDateTime agreedAt,
        String reopenedBy,
        java.time.OffsetDateTime reopenedAt,
        String reopenReason
) {
}
