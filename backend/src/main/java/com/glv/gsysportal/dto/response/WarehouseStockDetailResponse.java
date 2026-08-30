package com.glv.gsysportal.dto.response;

import java.util.List;

/** GET /api/warehouse-stock/{sku} (Phase 8-G 10章 - "全WarehouseのQty"
 * for one SKU; rendered as a List-row Drawer/Expand on the Frontend, not a
 * separate top-level screen). */
public record WarehouseStockDetailResponse(
        String sku,
        String itemName,
        String brandCode,
        String brandName,
        List<WarehouseStockSummaryResponse> warehouses) {
}
