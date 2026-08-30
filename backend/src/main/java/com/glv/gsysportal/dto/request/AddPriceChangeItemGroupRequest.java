package com.glv.gsysportal.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Add every SKU belonging to one Item Group to a Change Set in a single
 * call (target-price-change-workflow.md 9章's "Item Groupを起点とした複数
 * 選択" - resolved server-side by {@code LegacyPriceReadRepository}, never
 * by the Frontend posting a large SKU array). Already-present SKUs are
 * skipped, not duplicated (idempotent re-apply). */
public record AddPriceChangeItemGroupRequest(@NotBlank String itemGrpCd) {
}
