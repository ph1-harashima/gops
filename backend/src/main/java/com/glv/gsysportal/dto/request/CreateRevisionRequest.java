package com.glv.gsysportal.dto.request;

/**
 * POST /api/orders/{id}/revisions - "修正版を作成" (Phase 7-C5 13章). {@code reason}
 * is mandatory (400 REVISION_REASON_REQUIRED otherwise). When
 * {@code applyConfirmedValues} is true, every line's {@code orderQty} on the
 * live {@code portal_order_detail} is overwritten with the current Response's
 * {@code confirmedQty} wherever the Supplier actually answered (never for a
 * still-unanswered line) - written via the SAME
 * {@code ORDER_QTY_CHANGED} Audit event as any other Draft edit (7-C5 13章's
 * "must never auto-confirm" - this is an explicit ADMIN opt-in per call, off
 * by default, and only pre-fills a value the ADMIN can still edit again
 * afterwards via the ordinary Draft screen before the next Send).
 * {@code requestedDelivery} is Order-header-level in this schema (not
 * per-line) and is deliberately left untouched by this flag - the ADMIN edits
 * it manually via the existing Draft screen if needed.
 */
public record CreateRevisionRequest(
        String reason,
        boolean applyConfirmedValues
) {
}
