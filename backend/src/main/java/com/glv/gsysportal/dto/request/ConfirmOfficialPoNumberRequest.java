package com.glv.gsysportal.dto.request;

/** Phase 9-A / BR-08 (docs/gulliver-20260917-confirmed-business-rules.md):
 * the Excel-contract delivery/shipping/payment fields that have no other
 * home (docs/official-po-integration-detailed-design.md §3.2). The Official
 * PO No. itself is no longer part of this request - BR-08 auto-numbers it
 * at Official PO Integration Request creation time
 * ({@link com.glv.gsysportal.service.OfficialPoNumberGenerator}), so there
 * is nothing left for staff to enter or confirm for the number itself. All
 * fields here remain optional free text. */
public record ConfirmOfficialPoNumberRequest(
        String deliveryWeek,
        String deliveryDate,
        String shipVia,
        String shipTerm,
        String paymentTerm
) {
}
