package com.glv.gsysportal.dto.response;

import java.time.LocalDate;

/**
 * Arrival List row / Arrival Detail header (Phase 8-G 5章/6章). All 4
 * quantities (orderedQty/invoiceQty/arrivalQty/stockInQty) are plain Source
 * Facts shown side by side - no computed "残数"/"未入荷"/discrepancy field
 * exists here (Phase 8-G 7章's explicit prohibition; see
 * ArrivalListQuery.sql for exactly how each is derived and at what
 * granularity). {@code warehouseReportStatus}/{@code warehouseReportResult}
 * are Legacy's own free-text values passed through unmodified - never a
 * Portal-invented Status (4章).
 */
public record ArrivalSummaryResponse(
        String supplierCode,
        String supplierName,
        String poNumber,
        String invoiceNumber,
        String brandCode,
        String brandName,
        String blNumber,
        String vesselNumber,
        Integer orderedQty,
        Integer invoiceQty,
        Integer arrivalQty,
        Integer stockInQty,
        LocalDate etd,
        LocalDate eta,
        LocalDate etaWarehouse,
        LocalDate stockInDate,
        String warehouseReportStatus,
        String warehouseReportResult) {
}
