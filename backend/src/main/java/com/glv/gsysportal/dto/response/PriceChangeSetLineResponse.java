package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;

/**
 * One Price Change Set Detail line, combining the Baseline Snapshot with a
 * live Legacy re-read and Margin Preview (target-price-change-workflow.md
 * 10章's display table: 旧価格|新価格|差額|変更率|原価|利益|利益率). Pure
 * display data - no threshold/warning classification exists on this record
 * (Phase 8-B Section 5 forbids it).
 */
public record PriceChangeSetLineResponse(
        Long detailId,
        String itemCd,
        String itemName,
        String brandCode,
        String itemGrpCd,
        BigDecimal baselinePrcSellWTax,
        BigDecimal currentPrcSellWTax,
        BigDecimal costWTax,
        BigDecimal marginAmount,
        BigDecimal marginRate,
        BigDecimal proposedPrcSellWTax,
        BigDecimal proposedMarginAmount,
        BigDecimal proposedMarginRate,
        BigDecimal priceDifference,
        BigDecimal percentageChange,
        String concurrencyStatus) {

    /** Live Legacy value matches the Baseline captured when this Detail was added. */
    public static final String CONCURRENCY_UNCHANGED = "UNCHANGED";
    /** Live Legacy value differs from the Baseline - "Legacy側で変更されて
     * います" fact only (Section 8) - no Policy (block/allow/warn) is decided here. */
    public static final String CONCURRENCY_CHANGED = "CHANGED";
    /** The SKU could not be re-read from Legacy at all (deleted, or item_grp/
     * brand relationship broken) - distinguished from CHANGED because "no
     * longer exists" is more fundamental than "a field differs", mirroring
     * PO_NOT_FOUND's precedence over NOT_BASELINED in the Order Concurrency
     * design (excel-legacy-concurrency-control.md 6章/8章) - a design
     * REFERENCE, not a copy: this Foundation compares plain field equality
     * on 2 columns, not a Fingerprint/Diff Engine (13章's own "発注実装を
     * 機械的にコピーしない"). */
    public static final String CONCURRENCY_NOT_AVAILABLE = "NOT_AVAILABLE";
}
