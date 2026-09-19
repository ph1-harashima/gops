package com.glv.gsysportal.service;

import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;

/** BR-09 (docs/gulliver-20260917-confirmed-business-rules.md): "DOMESTICの
 * 具体的な計算式はまだ確定していません...今回は絶対に国内向け計算式を
 * 推測で実装しないでください". Returns {@code null} unconditionally - never
 * silently falls back to {@link OverseasRecommendedQtyStrategy}'s formula,
 * which would make an unconfirmed number look like a real Recommended Qty.
 * The Frontend renders this {@code null} as "国内向け推奨数量計算ルールは
 * 設定準備中" rather than blank/zero, so a DOMESTIC-classified Supplier's
 * absent number is never mistaken for "0 recommended" either. */
public class DomesticRecommendedQtyStrategy implements RecommendedQtyStrategy {

    @Override
    public Integer calculate(LegacyStockRow row) {
        return null;
    }
}
