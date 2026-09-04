package com.glv.gsysportal.service.integration;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/**
 * Phase 9-E (Production PO Workflow §10): the Production shape of real
 * Email delivery - Config/Interface/Adapter implemented per the user's
 * explicit instruction, deliberately never connected in this environment.
 *
 * <p><b>This class can never actually run here</b>: {@code @Profile("production")}
 * means Spring only creates this bean under an active "production" profile,
 * and {@link com.glv.gsysportal.safety.SafetyGuardEnvironmentPostProcessor}
 * refuses to even start {@code SpringApplication.run()} under that profile
 * (or any profile outside local/demo/test) - so this code path is provably
 * unreachable, not merely "not tested" (mirrors {@code ProductionImportFolderAdapter}'s
 * own precedent exactly). Standard {@code spring.mail.*} properties
 * (host/port/username/password) are read here rather than a bespoke
 * {@code app.*} namespace, so a real Production deploy can use Spring
 * Boot's own well-documented configuration surface without this codebase
 * inventing a parallel one - {@code MailSenderAutoConfiguration} itself
 * stays excluded (application.yml) so this remains the ONLY place a
 * {@code JavaMailSender} is ever constructed.
 */
@Component
@Profile("production")
public class SmtpEmailSenderAdapter implements EmailSenderPort {

    private final String host;
    private final int port;
    private final String username;
    private final String password;

    public SmtpEmailSenderAdapter(@Value("${spring.mail.host:}") String host,
                                   @Value("${spring.mail.port:587}") int port,
                                   @Value("${spring.mail.username:}") String username,
                                   @Value("${spring.mail.password:}") String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    @Override
    public void send(EmailEnvelope envelope) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(envelope.from());
            helper.setTo(envelope.to().toArray(new String[0]));
            if (!envelope.cc().isEmpty()) {
                helper.setCc(envelope.cc().toArray(new String[0]));
            }
            helper.setSubject(envelope.subject());
            helper.setText(envelope.body());
            if (envelope.attachmentBytes() != null && envelope.attachmentFileName() != null) {
                helper.addAttachment(envelope.attachmentFileName(), new ByteArrayResource(envelope.attachmentBytes()));
            }
            sender.send(message);
        } catch (MessagingException | MailException e) {
            throw new EmailSendException("Failed to send Email via SMTP", e);
        }
    }
}
