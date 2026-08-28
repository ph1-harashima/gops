package com.glv.gsysportal.dto.request;

import java.time.LocalDate;
import java.util.List;

/**
 * PUT /api/orders/{id}/supplier-response (implementation instructions 10章).
 * Lines not present in {@code details} are left untouched (partial save -
 * same "only included lines are modified" pattern as UpdateDraftRequest).
 * For a line that IS included, {@code confirmedQty} is the full intended
 * value for that field: {@code null} explicitly means "not answered / clear
 * the answer", any integer (including 0) is a real answer - never omit this
 * field to mean "no change" for an included line (implementation
 * instructions 11章 "0 と null").
 */
public record SaveSupplierResponseRequest(
        LocalDate responseDate,
        String responseNote,
        List<LineUpdate> details
) {
    public record LineUpdate(
            Long detailId,
            Integer confirmedQty,
            LocalDate confirmedDelivery,
            String responseNote,
            /** Phase 7-C5 7章: explicitly selected only - {@code null} means
             * "leave unchanged", never inferred from confirmedQty. */
            String supplyStatus
    ) {
    }
}
