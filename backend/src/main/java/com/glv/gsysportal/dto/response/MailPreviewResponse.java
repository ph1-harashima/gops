package com.glv.gsysportal.dto.response;

import java.util.List;

/**
 * Phase 7-C3 9章: Mail Preview only - there is no Send API this Phase (10章's
 * Gate is never satisfiable: Integration CONFIRMED cannot be reached until
 * 7-C2B+). {@code subject}/{@code body} are null whenever any BLOCKED issue
 * is present (unresolved Contact/Template/officialPoNo) - never a partially
 * or incorrectly rendered mail.
 */
public record MailPreviewResponse(
        String from,
        List<String> to,
        List<String> cc,
        String subject,
        String body,
        AttachmentSummary attachment,
        List<MailPreviewIssue> issues
) {
    /** 7-C3 16章's Attachment Foundation: metadata only, no real File. */
    public record AttachmentSummary(
            String type,
            String fileName,
            boolean generated
    ) {
    }
}
