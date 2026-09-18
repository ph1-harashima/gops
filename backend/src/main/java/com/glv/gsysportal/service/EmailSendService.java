package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.OrderEmail;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.MailPreviewIssue;
import com.glv.gsysportal.dto.response.MailPreviewResponse;
import com.glv.gsysportal.dto.response.OrderEmailResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.EmailAttachmentNotReadyException;
import com.glv.gsysportal.exception.EmailChannelNotApplicableException;
import com.glv.gsysportal.exception.EmailPreviewBlockedException;
import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.OrderEmailRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import com.glv.gsysportal.service.integration.EmailEnvelope;
import com.glv.gsysportal.service.integration.EmailSendException;
import com.glv.gsysportal.service.integration.EmailSenderPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * "メール送信" (Production PO Workflow §5/Phase 9-E). Sends exactly what
 * {@link MailPreviewService} resolves - reused directly, not re-implemented,
 * so there is exactly one source of truth for recipient/content logic
 * (design decision documented in the Implementation Plan). Requires the
 * resolved Manufacturer Channel to be EMAIL (never callable for an
 * EDI-channel manufacturer, §6) and the Official PO Excel to already be
 * generated (the current, only supported Attachment type).
 */
@Service
public class EmailSendService {

    static final String OPERATION_TYPE_EMAIL_SEND = "EMAIL_SEND";

    private final PortalOrderRepository portalOrderRepository;
    private final OrderEmailRepository orderEmailRepository;
    private final OfficialPoIntegrationRequestRepository integrationRequestRepository;
    private final AuditEventRepository auditEventRepository;
    private final ManufacturerChannelResolutionService channelResolutionService;
    private final MailPreviewService mailPreviewService;
    private final OfficialPoExcelGenerationService excelGenerationService;
    private final EmailSenderPort emailSenderPort;
    private final IdempotencyService idempotencyService;
    private final PortalUserRepository portalUserRepository;

    public EmailSendService(PortalOrderRepository portalOrderRepository,
                             OrderEmailRepository orderEmailRepository,
                             OfficialPoIntegrationRequestRepository integrationRequestRepository,
                             AuditEventRepository auditEventRepository,
                             ManufacturerChannelResolutionService channelResolutionService,
                             MailPreviewService mailPreviewService,
                             OfficialPoExcelGenerationService excelGenerationService,
                             EmailSenderPort emailSenderPort,
                             IdempotencyService idempotencyService,
                             PortalUserRepository portalUserRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.orderEmailRepository = orderEmailRepository;
        this.integrationRequestRepository = integrationRequestRepository;
        this.auditEventRepository = auditEventRepository;
        this.channelResolutionService = channelResolutionService;
        this.mailPreviewService = mailPreviewService;
        this.excelGenerationService = excelGenerationService;
        this.emailSenderPort = emailSenderPort;
        this.idempotencyService = idempotencyService;
        this.portalUserRepository = portalUserRepository;
    }

    /** GET-equivalent: current Send state, any authenticated user (matches
     * every other read endpoint's visibility). */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public OrderEmailResponse getStatus(Long orderId) {
        if (!portalOrderRepository.existsById(orderId)) {
            throw new DraftNotFoundException(orderId);
        }
        int revisionNo = OfficialPoIntegrationService.targetRevisionNo(
                portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId)));
        return orderEmailRepository.findByPortalOrderIdAndRevisionNo(orderId, revisionNo)
                .map(this::toResponse)
                .orElseGet(() -> OrderEmailResponse.notSent(orderId));
    }

    /**
     * ADMIN-only Business Action. Idempotent via {@link IdempotencyService}
     * (same pattern as Import Folder placement, Phase 9-B): a repeat call
     * while already SENT is a no-op; a call while FAILED retries (the same
     * resolved envelope/attachment, re-resolved fresh each attempt - Master
     * data can change between attempts, so a stale resolution should never
     * be trusted, same principle as Preflight's own re-run-every-call design).
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OrderEmailResponse send(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        int revisionNo = OfficialPoIntegrationService.targetRevisionNo(order);

        Optional<OrderEmail> existing = orderEmailRepository.findByPortalOrderIdAndRevisionNo(orderId, revisionNo);
        if (existing.isPresent() && OrderEmail.STATUS_SENT.equals(existing.get().getStatus())) {
            return toResponse(existing.get()); // already sent - idempotent no-op
        }

        String resolvedChannel = channelResolutionService.resolve(order.getSupplierCode(), order.getBrandCode());
        if (!"EMAIL".equals(resolvedChannel)) {
            throw new EmailChannelNotApplicableException(orderId, resolvedChannel);
        }

        MailPreviewResponse preview = mailPreviewService.preview(orderId, performedBy);
        boolean blocked = preview.issues().stream().anyMatch(i -> MailPreviewIssue.SEVERITY_BLOCKED.equals(i.severity()));
        if (blocked) {
            throw new EmailPreviewBlockedException(orderId);
        }

        OfficialPoIntegrationRequest integrationRequest = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(orderId, revisionNo)
                .orElseThrow(() -> new EmailAttachmentNotReadyException(orderId));
        if (integrationRequest.getGeneratedFileKey() == null) {
            throw new EmailAttachmentNotReadyException(orderId);
        }

        String idempotencyKey = orderId + "-" + revisionNo;
        IdempotencyService.IdempotencyClaim claim = idempotencyService.claim(
                OPERATION_TYPE_EMAIL_SEND, orderId.toString(), idempotencyKey);
        if (!claim.claimed()) {
            return existing.map(this::toResponse).orElseGet(() -> OrderEmailResponse.notSent(orderId));
        }

        OffsetDateTime now = OffsetDateTime.now();
        OrderEmail orderEmail = existing.orElseGet(() -> {
            OrderEmail e = new OrderEmail();
            e.setPortalOrderId(orderId);
            e.setRevisionNo(revisionNo);
            e.setCreatedAt(now);
            return e;
        });
        orderEmail.setFromAddress(preview.from());
        orderEmail.setToAddresses(String.join(",", preview.to()));
        orderEmail.setCcAddresses(String.join(",", preview.cc()));
        orderEmail.setSubject(preview.subject());
        orderEmail.setBody(preview.body());
        orderEmail.setAttachmentType(OrderEmail.ATTACHMENT_TYPE_OFFICIAL_PO_EXCEL);
        orderEmail.setAttachmentFileKey(integrationRequest.getGeneratedFileKey());
        orderEmail.setUpdatedAt(now);

        byte[] attachmentBytes = excelGenerationService.load(integrationRequest.getGeneratedFileKey());
        String attachmentFileName = (integrationRequest.getOfficialPoNo() == null
                ? "official-po" : integrationRequest.getOfficialPoNo()) + ".xlsx";
        EmailEnvelope envelope = new EmailEnvelope(
                preview.from(), preview.to(), preview.cc(), preview.subject(), preview.body(),
                attachmentFileName, attachmentBytes);

        try {
            emailSenderPort.send(envelope);
        } catch (EmailSendException e) {
            orderEmail.markFailed("EMAIL_SEND_FAILED", e.getMessage(), now);
            OrderEmail failed = orderEmailRepository.save(orderEmail);
            idempotencyService.markFailed(claim.operation().getId(), "EMAIL_SEND_FAILED");
            auditEventRepository.save(new AuditEvent(orderId, null,
                    AuditEvent.EMAIL_SEND_FAILED, null, null, e.getMessage(), performedBy, now));
            return toResponse(failed);
        }

        orderEmail.markSent(now, performedBy);
        OrderEmail saved = orderEmailRepository.save(orderEmail);
        idempotencyService.markSucceeded(claim.operation().getId());
        auditEventRepository.save(new AuditEvent(orderId, null,
                AuditEvent.EMAIL_SENT, null, null, preview.subject(), performedBy, now));
        return toResponse(saved);
    }

    private OrderEmailResponse toResponse(OrderEmail e) {
        String sentByDisplayName = e.getSentBy() == null ? null
                : portalUserRepository.findByUsername(e.getSentBy()).map(PortalUser::getDisplayName).orElse(null);
        return new OrderEmailResponse(
                e.getPortalOrderId(), e.getRevisionNo(), e.getStatus(),
                splitAddresses(e.getToAddresses()), splitAddresses(e.getCcAddresses()),
                e.getSubject(), e.getSentAt(), e.getSentBy(), sentByDisplayName,
                e.getErrorCode(), e.getErrorMessage(), e.getRetryCount()
        );
    }

    private static List<String> splitAddresses(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(","));
    }
}
