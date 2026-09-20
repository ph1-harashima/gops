package com.glv.gsysportal.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Stock/Sales List row / Detail (Phase 8-H 4章/7章). Reuses the exact same
 * Legacy Read Query as Order Candidate List / SKU Detail
 * ({@code LegacyStockReadRepository}, constructed from
 * {@code RecommendedQtyReadQuery.sql}) - no new calculation logic (8章).
 *
 * <p><b>Naming/meaning, Source Confirmed (Phase 8-C, RE Document
 * legacy-stock-sales-data-reverse-engineering.md)</b>:
 * <ul>
 *   <li>{@code currentMonthSalesQty} mirrors {@code MS_STK.SOLD_QTY} - the
 *   Tempostar order CSV-derived CURRENT MONTH CUMULATIVE shipment quantity.
 *   It is NOT a daily figure, NOT a rolling-30-day figure, and NOT a
 *   Sales Trend/History value - Legacy holds only this single current-month
 *   number, nothing else (no historical months are ever retained).</li>
 *   <li>{@code currentStock} mirrors the SAME 'XX' aggregate {@code ms_stk}
 *   row's {@code STK_QTY} that Order Candidate List/SKU Detail already
 *   read - it is NOT proven Source-identical to the per-warehouse
 *   {@code MS_STK.STK_QTY} rows Phase 8-G's Warehouse Stock Visibility
 *   Foundation reads (different granularity: 'XX' aggregate vs. per-
 *   warehouse rows) - never merged or compared against Warehouse Stock
 *   values (Phase 8-H 13章).</li>
 *   <li>{@code updatedAt} mirrors {@code ms_stk.update_datetime} for that
 *   SAME 'XX' row - a neutral "G-SYSデータ更新日時", never re-labeled as a
 *   Sales- or Warehouse-specific timestamp (12章).</li>
 * </ul>
 * {@code recommendedQty} is the existing calc4 value (Order Candidate List/
 * SKU Detail's own Recommended Qty Logic, unmodified - 9章), shown here only
 * as a reference figure, never a new Business Rule.
 */
public record StockSalesSummaryResponse(
        String sku,
        String itemName,
        String brandCode,
        String brandName,
        String supplierCode,
        String supplierName,
        Integer currentStock,
        Integer currentMonthSalesQty,
        Integer openPoQty,
        Integer openArrivalQty,
        Integer recommendedQty,
        String leadTime,
        String itemStatus,
        LocalDateTime updatedAt,
        /** Post-Freeze Business Refinement (re-audit doc §10-5): same merged
         * Display-Priority value as Candidate List/Order History Detail. */
        String restockSource,
        LocalDate restockDate) {
}
