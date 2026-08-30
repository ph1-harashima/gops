package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

/**
 * Price Change List row (target-price-change-workflow.md 15章 "Price Change
 * List" minimum columns: Change Set ID / Status / 作成者 / 対象件数 /
 * 最終更新日時). Deliberately does NOT carry the Detail lines themselves -
 * the List screen must not hold every Change Set's full line data at once
 * (9章's large-SKU-count warning applies at the List level too).
 */
public record PriceChangeSetSummary(
        Long id,
        String status,
        String note,
        String createdByDisplayName,
        OffsetDateTime createdAt,
        int detailCount,
        OffsetDateTime updatedAt) {
}
