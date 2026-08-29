package com.glv.gsysportal.repository.legacy.row;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Phase 7-C6: full commercial header row read from TR_PO for the Canonical
 * Snapshot (docs/excel-legacy-concurrency-control.md 2章's candidate field
 * list) - a superset of {@link LegacyPoHeaderRow} (7-C7A's Fulfillment-only
 * subset). Kept as its own row type deliberately, rather than widening
 * {@code LegacyPoHeaderRow} itself - see
 * {@code LegacyPoConcurrencyReadRepository}'s Javadoc for why.
 */
public record LegacyPoConcurrencyHeaderRow(
        String poNo,
        String status,
        String poType,
        String supplierCode,
        String brandCode,
        LocalDate orderDate,
        String currency,
        String delivWeek,
        String delivDate,
        BigDecimal amtTtl) {
}
