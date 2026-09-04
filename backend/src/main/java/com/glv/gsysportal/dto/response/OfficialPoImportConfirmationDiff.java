package com.glv.gsysportal.dto.response;

/**
 * Phase 9-C: one SKU-level discrepancy between Portal's current Order
 * lines and what Legacy TR_PO_DTL actually holds for the same PO No.
 * {@code expectedQty} null means the SKU exists in Legacy but not in
 * Portal's current lines (removed/never sent); {@code actualQty} null
 * means the SKU is in Portal's current lines but Legacy has no matching
 * line (not yet imported, or removed on Legacy's side).
 */
public record OfficialPoImportConfirmationDiff(
        String skuCode,
        Integer expectedQty,
        Integer actualQty
) {
}
