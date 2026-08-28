package com.glv.gsysportal.dto.response;

/** One row of the Brand別 breakdown table (implementation instructions
 * Step 5 3章). Clicking a row navigates the Frontend to the corresponding
 * screen pre-filtered by brandCode + the relevant Status/condition. */
public record DashboardBrandRow(
        String brandCode,
        String brandName,
        int candidateCount,
        int outOfStockCount,
        int draftCount,
        int awaitingSupplierCount,
        int attentionCount
) {
}
