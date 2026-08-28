package com.glv.gsysportal.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Phase 7-C3 12章: used for both create (POST) and update (PUT). */
public record MailTemplateRequest(
        @NotBlank String templateName,
        @NotBlank String templateType,
        String supplierCode,
        String brandCode,
        @NotBlank String language,
        @NotBlank String subjectTemplate,
        @NotBlank String bodyTemplate,
        String attachmentType,
        boolean active
) {
}
