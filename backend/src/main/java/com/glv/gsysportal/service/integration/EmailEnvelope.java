package com.glv.gsysportal.service.integration;

import java.util.List;

/** Phase 9-E: a fully-resolved Email ready to send - everything an
 * {@link EmailSenderPort} needs, already resolved by the caller
 * ({@code EmailSendService}, reusing {@code MailPreviewService}'s own
 * Contact/Template/Admin-CC resolution so there is exactly one source of
 * truth for recipient/content logic, never a second compose path). */
public record EmailEnvelope(
        String from,
        List<String> to,
        List<String> cc,
        String subject,
        String body,
        String attachmentFileName,
        byte[] attachmentBytes
) {
}
