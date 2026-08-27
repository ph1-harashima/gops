package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderDraftResponse(
        Long id,
        String draftNo,
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
        List<String> warningCodes
) {
}
