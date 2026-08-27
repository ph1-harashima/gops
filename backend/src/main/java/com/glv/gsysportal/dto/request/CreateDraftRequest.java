package com.glv.gsysportal.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

/**
 * Only SKU identifiers and Header fields the user actually chooses go here.
 * Deliberately NO recommendedQty/currentStock/unitPrice/supplier/etc. fields
 * exist on this request - the Backend re-fetches all of that from Legacy by
 * SKU (implementation instructions 4章). Any such fields sent by a client are
 * silently ignored since this DTO has no place to bind them.
 */
public record CreateDraftRequest(
        @NotEmpty List<String> skus,
        LocalDate orderDate,
        LocalDate requestedDelivery,
        String remark
) {
}
