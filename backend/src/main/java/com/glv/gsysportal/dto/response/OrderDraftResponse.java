package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderDraftResponse(
        Long id,
        String draftNo,
        String prototypePoNo,
        String supplierCode,
        String supplierName,
        String brandCode,
        String brandName,
        LocalDate orderDate,
        LocalDate requestedDelivery,
        String currency,
        String status,
        String remark,
        int totalQty,
        BigDecimal totalAmount,
        String dataSource,
        String createdBy,
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt,
        List<OrderDraftDetailResponse> details,
        List<String> warningCodes,
        /** Phase 7-C1 12章: the reason from the latest RETURNED_FOR_CORRECTION
         * Audit row, ONLY while it is the most recent approval-flow event on
         * a DRAFT-status Order (i.e. the Draft is back precisely because it
         * was returned); null otherwise. Read-time derived, never stored on
         * the Order itself. */
        String returnReason
) {
}
