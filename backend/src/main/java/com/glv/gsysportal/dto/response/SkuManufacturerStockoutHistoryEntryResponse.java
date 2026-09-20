package com.glv.gsysportal.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Post-Freeze Business Refinement 2
 * (docs/gops-manufacturer-stockout-information-management.md §11/§21) -
 * one entry in the Business-facing Manufacturer Stockout timeline. A
 * direct view of {@code SkuManufacturerStockoutHistory} - always the FULL
 * state at that point in time, not a diff.
 */
public record SkuManufacturerStockoutHistoryEntryResponse(
        String stockoutStatus,
        LocalDate expectedRestockDate,
        boolean unknown,
        Integer shortageQty,
        LocalDate informationReceivedDate,
        String contactMethod,
        String memo,
        String recordedBy,
        OffsetDateTime recordedAt
) {
}
