package com.glv.gsysportal.repository.legacy.row;

import java.time.LocalDate;

/** Post-Freeze Business Refinement - one row per SKU with a still-open
 * Arrival, {@code expectedArrivalDate} being {@code eta_wh} (preferred) or
 * {@code eta} (fallback). See ArrivalExpectedBySkuQuery.sql for the exact
 * derivation. */
public record LegacyExpectedArrivalRow(String skuCode, LocalDate expectedArrivalDate) {
}
