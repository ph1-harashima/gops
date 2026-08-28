package com.glv.gsysportal.dto.response;

/** Phase 7-C3 9章/10章: one Mail Preview finding. Same shape/convention as
 * {@link OfficialPoPreflightIssue} (code/severity are internal, resolved
 * client-side via i18n; message is an always-English technical detail). A
 * BLOCKED-severity issue anywhere means Subject/Body are not rendered
 * (MailPreviewResponse.subject/body are null) - never a silently-wrong or
 * partially-substituted mail. */
public record MailPreviewIssue(
        String code,
        String severity,
        String message
) {
    public static final String SEVERITY_BLOCKED = "BLOCKED";
    public static final String SEVERITY_WARNING = "WARNING";
}
