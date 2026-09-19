package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import com.glv.gsysportal.service.integration.EmailEnvelope;
import com.glv.gsysportal.service.integration.EmailSendException;
import com.glv.gsysportal.service.integration.EmailSenderPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * BR-03 (docs/gulliver-20260917-confirmed-business-rules.md): "メーカーへ取消
 * 連絡" - a deliberately minimal, best-effort notice, NOT the full Mail
 * Template/Preview engine ({@code MailPreviewService}/{@code EmailSendService}
 * are scoped to the Purchase Order document itself, not a Cancel notice).
 * Reuses the SAME {@link EmailSenderPort} ({@link
 * com.glv.gsysportal.service.integration.LoggingEmailSenderAdapter} in
 * Local/Demo/Test - never a real SMTP connection, matching BR-03's explicit
 * "Local/DemoではSimulationで構いません") and the same Supplier Contact
 * Master ({@link SupplierContactResolutionService}) every other Manufacturer
 * communication already resolves through - never a second, independent
 * Contact source.
 *
 * <p>Never blocks the Cancel Approval itself: if no TO Contact is
 * registered for this Order's Supplier/Brand, the notice is skipped (not
 * retried, not queued) and {@link #notify} simply reports that it was not
 * sent - {@link OfficialPoIntegrationService#approveCancel} still completes
 * the CANCELLED transition either way, and the Audit Trail records whichever
 * outcome actually happened.
 */
@Service
public class OfficialPoCancelNotificationService {

    private final SupplierContactResolutionService contactResolutionService;
    private final EmailSenderPort emailSenderPort;
    private final PortalUserRepository portalUserRepository;

    public OfficialPoCancelNotificationService(SupplierContactResolutionService contactResolutionService,
                                                EmailSenderPort emailSenderPort,
                                                PortalUserRepository portalUserRepository) {
        this.contactResolutionService = contactResolutionService;
        this.emailSenderPort = emailSenderPort;
        this.portalUserRepository = portalUserRepository;
    }

    public record NotificationResult(boolean sent, String recipient, String note) {
    }

    public NotificationResult notify(PortalOrder order, OfficialPoIntegrationRequest request, String reason, String performedBy) {
        List<SupplierContact> toContacts = contactResolutionService.resolve(
                order.getSupplierCode(), order.getBrandCode(), SupplierContact.CONTACT_TYPE_TO);
        if (toContacts.isEmpty()) {
            return new NotificationResult(false, null, "No TO Contact registered for this Supplier/Brand - notification skipped");
        }

        List<String> to = toContacts.stream().map(SupplierContact::getEmail).toList();
        String from = resolveFrom(performedBy);
        String poNo = request.getOfficialPoNo() == null ? "(unassigned)" : request.getOfficialPoNo();
        String subject = "[Cancelled] Official PO " + poNo;
        String body = "Official PO " + poNo + " has been cancelled.\nReason: " + reason;

        try {
            emailSenderPort.send(new EmailEnvelope(from, to, List.of(), subject, body, List.of()));
        } catch (EmailSendException e) {
            return new NotificationResult(false, String.join(",", to), "Send failed: " + e.getMessage());
        }
        return new NotificationResult(true, String.join(",", to), null);
    }

    private String resolveFrom(String performedBy) {
        Optional<PortalUser> sender = portalUserRepository.findByUsername(performedBy);
        return sender.map(u -> u.getDisplayName() == null ? u.getEmail() : u.getDisplayName() + " <" + u.getEmail() + ">")
                .orElse(performedBy);
    }
}
