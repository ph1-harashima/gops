package com.glv.gsysportal.repository.legacy.row;

import java.math.BigDecimal;

/**
 * Plain projection row for the reduced Legacy Read Query
 * (legacy/PriceReadQuery.sql, Phase 8-B). Deliberately NOT a JPA
 * {@code @Entity} - same convention as {@link LegacyStockRow} (Legacy is
 * read via JdbcTemplate + RowMapper, never entity-mapped, Technical Design
 * 4.2).
 */
public record LegacyPriceRow(
        String itemCd,
        String itemName,
        String brandCd,
        String brandName,
        String itemGrpCd,
        String itemStatus,
        Boolean discon,
        BigDecimal prcSellWTax,
        BigDecimal costThisMonthAvg,
        Boolean freeShipFlg,
        BigDecimal shipFee
) {
}
