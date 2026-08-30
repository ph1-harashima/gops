package com.glv.gsysportal.repository.legacy.row;

import java.time.LocalDateTime;

/**
 * Mirrors one physical-warehouse MS_STK row (WH_CD != 'XX' - the 'XX' row is
 * Legacy's own aggregate/primary row, not a real warehouse, and is
 * deliberately excluded by {@link com.glv.gsysportal.repository.legacy.WarehouseStockReadRepository}
 * - Phase 0.5/8-C confirmed Source Fact, docs/legacy-stock-sales-data-
 * reverse-engineering.md). {@code whCd} is shown as-is on the Frontend - no
 * Source Confirmed name exists for any code beyond '01' (Phase 8-F RE
 * Document 8章/17章), so none is invented here.
 */
public record LegacyWarehouseStockRow(
        String whCd,
        String itemCd,
        String itemName,
        String brandCd,
        String brandName,
        Integer stkQty,
        LocalDateTime updateDatetime) {
}
