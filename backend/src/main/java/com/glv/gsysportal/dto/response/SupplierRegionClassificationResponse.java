package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

public record SupplierRegionClassificationResponse(
        Long id,
        String supplierCode,
        String brandCode,
        String regionClassification,
        boolean active,
        String createdBy,
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
