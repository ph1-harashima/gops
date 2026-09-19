package com.glv.gsysportal.dto.request;

import jakarta.validation.constraints.NotBlank;

/** BR-08: used for both create (POST) and update (PUT). {@code codeType} is
 * {@code SUPPLIER} or {@code BRAND}; {@code businessCode} is the existing
 * Legacy Supplier/Brand Code (e.g. {@code SUP_ALPHA}); {@code shortCode} is
 * the 3-character abbreviation Gulliver has decided for it. */
public record OfficialPoShortCodeRequest(
        @NotBlank String codeType,
        @NotBlank String businessCode,
        @NotBlank String shortCode,
        boolean active
) {
}
