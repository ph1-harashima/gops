package com.glv.gsysportal.dto.request;

import java.time.LocalDate;

/** Post-Freeze Business Refinement (Type C, re-audit doc §9/§12). {@code
 * unknown} and {@code expectedRestockDate} are mutually exclusive -
 * validated in {@code SkuExpectedRestockService} before persisting. Neither
 * field is Required in the strict sense: submitting both null/false clears
 * the record back to "not yet looked into".
 *
 * <p>Post-Freeze Business Refinement 2
 * (docs/gops-manufacturer-stockout-information-management.md §6/§25) -
 * {@code stockoutStatus}/{@code shortageQty}/
 * {@code informationReceivedDate}/{@code contactMethod} added as trailing,
 * all-optional fields so the existing PUT contract stays backward
 * compatible: a caller sending only the original 3 fields (as JSON, field
 * names not position - Jackson) still works exactly as before, with these
 * four simply landing null. {@code stockoutStatus} - STOCKOUT /
 * LONG_TERM_STOCKOUT / RESOLVED, or null (not yet classified).
 * {@code contactMethod} - PHONE / EMAIL / ORDER_RESPONSE / OTHER, or null. */
public record SkuExpectedRestockRequest(
        LocalDate expectedRestockDate,
        boolean unknown,
        String memo,
        String stockoutStatus,
        Integer shortageQty,
        LocalDate informationReceivedDate,
        String contactMethod
) {
}
