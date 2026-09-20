package com.glv.gsysportal.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Post-Freeze Business Refinement (re-audit doc §8, Display Priority) - the
 * single merged view List/Detail screens render from, so the Frontend never
 * has to re-implement the Legacy-vs-Portal-Manual priority rule itself.
 *
 * <p>{@code source}:
 * <ul>
 *   <li>{@code LEGACY_EXPECTED_ARRIVAL} - Type A, READ ONLY, from
 *       {@code ArrivalReadRepository.findExpectedArrivalBySkus}. Always wins
 *       when present - Portal Manual never overrides it (§8).</li>
 *   <li>{@code PORTAL_MANUAL} - Type C, a real date, ADMIN/OPERATOR-entered.</li>
 *   <li>{@code PORTAL_MANUAL_UNKNOWN} - Type C, affirmatively recorded
 *       "未定" (not merely absent).</li>
 *   <li>{@code NONE} - nothing to show (no open Legacy Arrival, no Portal
 *       record at all).</li>
 * </ul>
 *
 * <p>{@code manualUpdatedBy}/{@code manualUpdatedAt}/{@code manualMemo}/
 * {@code manualDate}/{@code manualUnknown} are populated whenever a Portal
 * Manual record exists at all, even if {@code source} is currently
 * {@code LEGACY_EXPECTED_ARRIVAL} (Legacy winning display priority does not
 * erase the Manual record underneath it) - this lets the SKU Detail Edit
 * form always show "who last set this" AND prefill the actual Manual
 * date/未定 state, even while Legacy is the one currently displayed via
 * {@code date}/{@code source}. Without a separate {@code manualDate}/
 * {@code manualUnknown}, an Edit form reading the merged {@code date} while
 * Legacy wins would prefill with Legacy's own date and silently overwrite
 * the real Manual value on the next Save.
 */
public record SkuRestockExpectationResponse(
        String skuCode,
        String source,
        LocalDate date,
        String manualMemo,
        String manualUpdatedBy,
        OffsetDateTime manualUpdatedAt,
        LocalDate manualDate,
        boolean manualUnknown
) {
    public static final String SOURCE_LEGACY = "LEGACY_EXPECTED_ARRIVAL";
    public static final String SOURCE_PORTAL_MANUAL = "PORTAL_MANUAL";
    public static final String SOURCE_PORTAL_UNKNOWN = "PORTAL_MANUAL_UNKNOWN";
    public static final String SOURCE_NONE = "NONE";
}
