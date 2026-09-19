package com.glv.gsysportal.service;

import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;

/** BR-09 (docs/gulliver-20260917-confirmed-business-rules.md): Recommended
 * Qty計算方式はDomestic/Overseasで異なる - this is the Strategy switch point
 * {@link RecommendedQtyCalculator} dispatches through, so a future Domestic
 * formula (once confirmed) plugs in without touching any of the 4 existing
 * call sites (OrderCandidateService/OrderDraftPersistenceService/
 * SkuDetailService/StockSalesService) or the Overseas Legacy Logic itself. */
public interface RecommendedQtyStrategy {

    /** @return the Recommended Qty, or {@code null} when this Strategy has
     *          no confirmed calculation rule to apply yet (never a guessed
     *          number silently standing in for a real one). */
    Integer calculate(LegacyStockRow row);
}
