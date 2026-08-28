package com.glv.gsysportal.dto.response;

/** Lightweight Attention reference embedded in Order History / Supplier
 * Response views, so the Frontend can call
 * {@code POST /api/attentions/{id}/acknowledge} directly from either screen
 * (implementation instructions Step 5 2章). */
public record AttentionSummary(
        Long id,
        String attentionType
) {
}
