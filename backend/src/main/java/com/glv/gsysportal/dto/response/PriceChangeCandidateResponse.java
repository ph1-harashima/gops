package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;

/**
 * Product Selection search result row (target-price-change-workflow.md
 * 15章's Product Selection step, folded into the Create/Edit screen per that
 * Document's UI consolidation). No Japanese display text (Requirements MD
 * 30.12) - itemStatus is an internal code, resolved via i18n on the Frontend.
 */
public record PriceChangeCandidateResponse(
        String itemCd,
        String itemName,
        String brandCode,
        String brandName,
        String itemGrpCd,
        String itemStatus,
        BigDecimal prcSellWTax,
        BigDecimal costThisMonthAvg
) {
}
