package com.glv.gsysportal.service.integration;

import java.util.List;

/** Phase 9-E: a fully-resolved Email ready to send - everything an
 * {@link EmailSenderPort} needs, already resolved by the caller
 * ({@code EmailSendService}, reusing {@code MailPreviewService}'s own
 * Contact/Template/Admin-CC resolution so there is exactly one source of
 * truth for recipient/content logic, never a second compose path).
 *
 * <p>BR-01 (docs/gulliver-20260917-confirmed-business-rules.md): a
 * Manufacturer Send attaches BOTH the Official PO Excel and PDF (Excel is
 * the source Document, PDF is the same content as an unmodifiable
 * 発注書) - {@code attachments} therefore holds zero or more files, never a
 * single hardcoded slot. */
public record EmailEnvelope(
        String from,
        List<String> to,
        List<String> cc,
        String subject,
        String body,
        List<Attachment> attachments
) {

    public record Attachment(String fileName, byte[] bytes) {
    }
}
