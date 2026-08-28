package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

public record MailTemplateResponse(
        Long id,
        String templateName,
        String templateType,
        String supplierCode,
        String brandCode,
        String language,
        String subjectTemplate,
        String bodyTemplate,
        String attachmentType,
        boolean active,
        String createdBy,
        OffsetDateTime createdAt,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
