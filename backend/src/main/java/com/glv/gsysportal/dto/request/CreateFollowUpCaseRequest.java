package com.glv.gsysportal.dto.request;

/** POST /api/orders/{id}/follow-up-cases (Phase 7-C7A 12章): "問い合わせ対象
 * にする" - always an explicit human action, never auto-generated from a
 * Fulfillment calculation. {@code skuCode} null = an Order-level Case. */
public record CreateFollowUpCaseRequest(
        String skuCode,
        String reason,
        String note
) {
}
