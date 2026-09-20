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
 *
 * <p>Post-Freeze Business Refinement 2
 * (docs/gops-manufacturer-stockout-information-management.md §23) -
 * {@code source}/{@code date} above are kept EXACTLY as before (backward
 * compatible with every existing caller of this API) and still express the
 * simple Legacy-wins priority. But the requirements for this round are
 * explicit that Legacy Expected Arrival and Manufacturer Stockout
 * Information must never fully hide one another when they actually
 * disagree - so this response separately and always exposes the raw
 * Manufacturer Stockout fields ({@code stockoutStatus}/{@code shortageQty}/
 * {@code informationReceivedDate}/{@code contactMethod}), the raw
 * {@code legacyDate} (distinct from the merged {@code date}), and a
 * computed {@code hasConflict} flag - {@code true} only when Legacy has an
 * open Expected Arrival AND the Manufacturer's own status is still
 * {@code STOCKOUT}/{@code LONG_TERM_STOCKOUT} (i.e. the Manufacturer is
 * still telling us it's short even though Legacy shows an incoming
 * Arrival). Screens updated this round render both values side by side
 * when {@code hasConflict} is true, rather than relying on the merged
 * {@code source}/{@code date} alone.
 */
public record SkuRestockExpectationResponse(
        String skuCode,
        String source,
        LocalDate date,
        String manualMemo,
        String manualUpdatedBy,
        OffsetDateTime manualUpdatedAt,
        LocalDate manualDate,
        boolean manualUnknown,
        LocalDate legacyDate,
        String stockoutStatus,
        Integer shortageQty,
        LocalDate informationReceivedDate,
        String contactMethod,
        boolean hasConflict
) {
    public static final String SOURCE_LEGACY = "LEGACY_EXPECTED_ARRIVAL";
    public static final String SOURCE_PORTAL_MANUAL = "PORTAL_MANUAL";
    public static final String SOURCE_PORTAL_UNKNOWN = "PORTAL_MANUAL_UNKNOWN";
    public static final String SOURCE_NONE = "NONE";

    public static final String STOCKOUT_STATUS_STOCKOUT = "STOCKOUT";
    public static final String STOCKOUT_STATUS_LONG_TERM = "LONG_TERM_STOCKOUT";
    public static final String STOCKOUT_STATUS_RESOLVED = "RESOLVED";

    public static final String CONTACT_METHOD_PHONE = "PHONE";
    public static final String CONTACT_METHOD_EMAIL = "EMAIL";
    public static final String CONTACT_METHOD_ORDER_RESPONSE = "ORDER_RESPONSE";
    public static final String CONTACT_METHOD_OTHER = "OTHER";
}
