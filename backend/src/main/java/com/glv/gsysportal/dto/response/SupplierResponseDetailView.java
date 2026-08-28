package com.glv.gsysportal.dto.response;

import java.time.LocalDate;
import java.util.List;

/** confirmedQty is Integer (not int) so JSON {@code null} is preserved
 * distinctly from {@code 0} all the way to the client (implementation
 * instructions 11章). */
public record SupplierResponseDetailView(
        Long detailId,
        String sku,
        String itemName,
        int orderedQty,
        Integer confirmedQty,
        LocalDate requestedDelivery,
        LocalDate confirmedDelivery,
        String responseNote,
        boolean isConfirmed,
        List<AttentionSummary> attentions,
        List<String> warningCodes
) {
}
