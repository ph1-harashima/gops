package com.glv.gsysportal.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Phase 9-D: used for both create (POST) and update (PUT) - mirrors
 * {@code SupplierContactRequest}'s "every field replaced on update"
 * convention. {@code brandCode} is optional (null = "applies to every
 * Brand" - the supplier-only Resolution tier). */
public record ManufacturerChannelRequest(
        @NotBlank String supplierCode,
        String brandCode,
        @NotBlank String channel,
        boolean active
) {
}
