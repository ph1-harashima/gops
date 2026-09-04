package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

public record ManufacturerChannelResponse(
        Long id,
        String supplierCode,
        String brandCode,
        String channel,
        boolean active,
        String createdBy,
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
