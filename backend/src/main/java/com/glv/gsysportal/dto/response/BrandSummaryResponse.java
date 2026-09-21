package com.glv.gsysportal.dto.response;

/**
 * Stage 5E Targeted Remediation (RC-B, docs/real-data-audit/
 * gops-stage5e-targeted-remediation.md): one Brand's code/name pair - the
 * lightweight lookup {@code GET /api/brands} returns, replacing
 * {@code CandidateListPage}'s previous dependency on the full
 * {@code GET /api/dashboard} response for this single field.
 */
public record BrandSummaryResponse(String brandCode, String brandName) {
}
