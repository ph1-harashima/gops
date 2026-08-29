package com.glv.gsysportal.dto.request;

import java.util.List;

/**
 * POST /api/follow-up-cases/{id}/reorder-draft (Phase 7-C7A 16章). Reuses
 * {@code OrderDraftService.createDraft} verbatim - {@code skus} is the same
 * SKU-identifiers-only contract as {@link CreateDraftRequest} (the Backend
 * re-fetches Recommended Qty/Stock/Price from Legacy by SKU; the client
 * never sends a quantity, so no quantity is ever auto-decided from
 * Outstanding Qty here - 16章's explicit instruction).
 */
public record CreateReorderDraftRequest(
        List<String> skus,
        String reorderReason
) {
}
