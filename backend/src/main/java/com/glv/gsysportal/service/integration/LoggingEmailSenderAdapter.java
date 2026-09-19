package com.glv.gsysportal.service.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Phase 9-E: local/demo/test-only Email Sender Adapter - never opens a
 * socket, never resolves an SMTP host. Logs the full Envelope at INFO and
 * always succeeds, which is also this Phase's "Email Send Mock" test
 * vehicle (Production PO Workflow §12): {@code EmailSendServiceTest} mocks
 * {@link EmailSenderPort} directly via Mockito to exercise the failure/retry
 * path (this Adapter itself has no failure mode to trigger), while the real
 * Integration Test exercises this Adapter for the success path end-to-end.
 */
@Component
@Profile({"local", "demo", "test"})
public class LoggingEmailSenderAdapter implements EmailSenderPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSenderAdapter.class);

    @Override
    public void send(EmailEnvelope envelope) {
        String attachmentSummary = envelope.attachments().isEmpty() ? "(none)"
                : envelope.attachments().stream()
                        .map(a -> a.fileName() + " (" + (a.bytes() == null ? 0 : a.bytes().length) + " bytes)")
                        .reduce((a, b) -> a + ", " + b).orElse("(none)");
        log.info("[LoggingEmailSenderAdapter] Would send Email - from={} to={} cc={} subject={} attachments={}",
                envelope.from(), envelope.to(), envelope.cc(), envelope.subject(), attachmentSummary);
    }
}
