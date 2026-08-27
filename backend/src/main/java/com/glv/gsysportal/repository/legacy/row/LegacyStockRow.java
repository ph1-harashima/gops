package com.glv.gsysportal.repository.legacy.row;

/**
 * Plain projection row for the reduced Legacy Read Query (RecommendedQtyReadQuery.sql).
 * Deliberately NOT a JPA @Entity - see Technical Design 4.2 (Legacy is read via
 * JdbcTemplate + RowMapper, never entity-mapped).
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
        String currency
) {
}
