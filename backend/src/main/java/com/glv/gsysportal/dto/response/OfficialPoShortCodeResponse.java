package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

public record OfficialPoShortCodeResponse(
        Long id,
        String codeType,
        String businessCode,
        String shortCode,
        boolean active,
        String createdBy,
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
