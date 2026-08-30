package com.glv.gsysportal.repository.legacy.row;

/**
 * Plain projection row for the reduced Legacy Read Query (RecommendedQtyReadQuery.sql).
 * Deliberately NOT a JPA @Entity - see Technical Design 4.2 (Legacy is read via
 * JdbcTemplate + RowMapper, never entity-mapped).
 *
 * <p>{@code updateDatetime} added in Phase 8-H (Stock/Sales Visibility
 * Foundation) - mirrors {@code ms_stk.update_datetime} for the SAME 'XX'
 * aggregate row {@code monthlySales}/{@code currentStock} etc. already come
 * from (RecommendedQtyReadQuery.sql's {@code agg} alias) - a neutral "G-SYS
 * データ更新日時", never re-labeled as a Sales- or Warehouse-specific
 * timestamp (Phase 8-H 12章's explicit instruction).
 */
public record LegacyStockRow(
        String itemCd,
        String itemName,
        String brandCd,
        String brandName,
        String leadTime,
        String itemStatus,
        Boolean discon,
        Integer currentStock,
        Integer stkStandard,
        Integer monthlySales,
        Integer openPo,
        Integer openArrival,
        Integer openShip,
        String formula11,
        String formula12,
        String formula13,
        String formula14,
        String supplierCd,
        String supplierName,
        java.math.BigDecimal unitPrice,
        String currency,
        java.time.LocalDateTime updateDatetime
) {
}
