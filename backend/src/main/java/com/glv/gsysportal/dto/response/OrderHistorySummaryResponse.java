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
        // Phase 1 Final Cleanup (Order History Number Model Audit): the
        // Official PO No. (G-SYS/Excel/PDF/Manufacturer Send source of
        // truth) and its Revision were previously absent from this DTO -
        // the List could not show them even though prototypePoNo (Portal管理番号)
        // was already distinguishable from officialPoNo at the entity level.
        // Both null until Official PO integration first assigns them
        // (PortalOrder.officialPoNo / currentRevisionNo Javadoc) - never
        // substitute draftNo/prototypePoNo for these.
        String officialPoNo,
        Integer revisionNo,
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
