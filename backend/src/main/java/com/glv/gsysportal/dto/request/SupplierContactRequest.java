package com.glv.gsysportal.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Phase 7-C3 12章: used for both create (POST) and update (PUT) - every
 * field is replaced on update, matching this codebase's Draft PUT
 * convention. {@code brandCode}/{@code region}/{@code procurementType} are
 * optional (null = "applies to every Brand" for brandCode - the supplier-only
 * Resolution tier, 7-C3 5章; region/procurementType are CUSTOMER REVIEW
 * territory, unused by Resolution this Phase).
 */
public record SupplierContactRequest(
        @NotBlank String supplierCode,
        String brandCode,
        @NotBlank String contactName,
        @NotBlank String email,
        @NotBlank String contactType,
        @NotBlank String language,
        String region,
        String procurementType,
        boolean primary,
        boolean active
) {
}
