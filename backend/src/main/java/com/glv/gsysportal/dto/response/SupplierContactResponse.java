package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

public record SupplierContactResponse(
        Long id,
        String supplierCode,
        String brandCode,
        String contactName,
        String email,
        String contactType,
        String language,
        String region,
        String procurementType,
        boolean primary,
        boolean active,
        String createdBy,
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
