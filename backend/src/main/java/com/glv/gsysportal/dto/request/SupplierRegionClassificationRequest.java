package com.glv.gsysportal.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章):
 * used for both create (POST) and update (PUT). {@code brandCode} is
 * optional (null = "applies to every Brand" - the supplier-only Resolution
 * tier). */
public record SupplierRegionClassificationRequest(
        @NotBlank String supplierCode,
        String brandCode,
        @NotBlank String regionClassification,
        boolean active
) {
}
