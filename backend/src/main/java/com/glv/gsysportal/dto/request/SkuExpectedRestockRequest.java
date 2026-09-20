package com.glv.gsysportal.dto.request;

import java.time.LocalDate;

/** Post-Freeze Business Refinement (Type C, re-audit doc §9/§12). {@code
 * unknown} and {@code expectedRestockDate} are mutually exclusive -
 * validated in {@code SkuExpectedRestockService} before persisting. Neither
 * field is Required in the strict sense: submitting both null/false clears
 * the record back to "not yet looked into". */
public record SkuExpectedRestockRequest(
        LocalDate expectedRestockDate,
        boolean unknown,
        String memo
) {
}
