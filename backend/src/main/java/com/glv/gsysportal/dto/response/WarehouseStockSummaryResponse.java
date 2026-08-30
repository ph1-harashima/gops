package com.glv.gsysportal.dto.response;

import java.time.LocalDateTime;

/**
 * Warehouse Stock List row (Phase 8-G 8章/9章). {@code whCode} is shown
 * as-is (no Source Confirmed name exists for any code beyond '01' - Phase
 * 8-F RE Document 8章/17章) - the Frontend must never invent a Japanese
 * label for it. No "Sellable" flag exists here on purpose: the "sellable
 * warehouse" whitelist Phase 8-F found ({4,5,6,7,11,13}) lives only inside
 * Legacy Batch code (InvLogizeroStkImportBatch's threshold calc), not as a
 * stored/queryable column anywhere in Source, so it cannot be surfaced
 * without fabricating a field Source does not actually have.
 */
public record WarehouseStockSummaryResponse(
        String warehouseCode,
        String sku,
        String itemName,
        String brandCode,
        String brandName,
        Integer stockQty,
        LocalDateTime updatedAt) {
}
