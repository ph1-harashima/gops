package com.glv.gsysportal.service.integration;

/**
 * Phase 9-E (Production PO Workflow §5/Phase 5): Port for actually
 * delivering an {@link EmailEnvelope}. Exactly one implementation is ever
 * active per Spring profile - {@link LoggingEmailSenderAdapter} for
 * local/demo/test (never opens a socket), {@link SmtpEmailSenderAdapter}
 * for production (unreachable here - {@link
 * com.glv.gsysportal.safety.SafetyGuardEnvironmentPostProcessor} refuses to
 * start under any profile but local/demo/test regardless), mirroring
 * {@link OfficialPoImportFolderAdapter}'s own Port+Adapter shape exactly.
 */
public interface EmailSenderPort {

    /** @throws EmailSendException on any failure to deliver - the caller
     *          (EmailSendService) maps this to the OrderEmail row's FAILED
     *          state, never lets it propagate as a raw 500. */
    void send(EmailEnvelope envelope);
}
