package com.glv.gsysportal.dto.request;

/** Phase 9-A: "PO番号入力/確定UI" + the Excel-contract fields that have no
 * other home (docs/official-po-integration-detailed-design.md §3.2).
 * {@code officialPoNo} is validated in {@code OfficialPoIntegrationService}
 * (length only - no ID Code/separator structure, per the Working
 * Assumption not to hardcode an unconfirmed numbering rule). All other
 * fields are optional free text. */
public record ConfirmOfficialPoNumberRequest(
        String officialPoNo,
        String deliveryWeek,
        String deliveryDate,
        String shipVia,
        String shipTerm,
        String paymentTerm
) {
}
