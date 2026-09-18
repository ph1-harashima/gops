package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.IdempotentOperation;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.OrderEmail;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.MailPreviewResponse;
import com.glv.gsysportal.dto.response.OrderEmailResponse;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.OrderEmailRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import com.glv.gsysportal.service.integration.EmailEnvelope;
import com.glv.gsysportal.service.integration.EmailSendException;
import com.glv.gsysportal.service.integration.EmailSenderPort;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 9-E: the FAILED/retry path - {@link com.glv.gsysportal.service.integration.LoggingEmailSenderAdapter}
 * (the only real Adapter active in this environment) has no failure mode of
 * its own to trigger, so this pure Mockito unit test mocks
 * {@link EmailSenderPort} directly to exercise it (same idiom
 * {@code MailTemplateResolutionServiceTest} already established in this
 * codebase for a Repository-stubbed pure Unit Test).
 */
class EmailSendServiceRetryTest {

    private static final Long ORDER_ID = 42L;
    private static final String ADMIN = "admin-tester";

    private PortalOrder order() {
        PortalOrder order = new PortalOrder();
        order.setId(ORDER_ID);
        order.setSupplierCode("SUP_ALPHA");
        order.setBrandCode("BR_OUTDOOR");
        order.setCurrentRevisionNo(null); // targetRevisionNo -> 1
        return order;
    }

    private OfficialPoIntegrationRequest generatedRequest() {
        OfficialPoIntegrationRequest r = new OfficialPoIntegrationRequest();
        r.setPortalOrderId(ORDER_ID);
        r.setRevisionNo(1);
        r.setOfficialPoNo("SUPA-OUTD-TEST");
        r.setGeneratedFileKey("some-file-key.xlsx");
        return r;
    }

    private MailPreviewResponse unblockedPreview() {
        return new MailPreviewResponse("sender@example.com", List.of("to@example.com"), List.of(),
                "Subject", "Body", new MailPreviewResponse.AttachmentSummary("OFFICIAL_PO_EXCEL", "PO.xlsx", false), List.of());
    }

    @Test
    void sendFailureMarksOrderEmailFailedAndAllowsRetry() {
        PortalOrderRepository portalOrderRepository = mock(PortalOrderRepository.class);
        OrderEmailRepository orderEmailRepository = mock(OrderEmailRepository.class);
        OfficialPoIntegrationRequestRepository integrationRequestRepository = mock(OfficialPoIntegrationRequestRepository.class);
        AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
        ManufacturerChannelResolutionService channelResolutionService = mock(ManufacturerChannelResolutionService.class);
        MailPreviewService mailPreviewService = mock(MailPreviewService.class);
        OfficialPoExcelGenerationService excelGenerationService = mock(OfficialPoExcelGenerationService.class);
        EmailSenderPort emailSenderPort = mock(EmailSenderPort.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        PortalUserRepository portalUserRepository = mock(PortalUserRepository.class);

        PortalOrder order = order();
        when(portalOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(channelResolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR")).thenReturn("EMAIL");
        when(mailPreviewService.preview(eq(ORDER_ID), anyString())).thenReturn(unblockedPreview());
        when(integrationRequestRepository.findByPortalOrderIdAndRevisionNo(ORDER_ID, 1))
                .thenReturn(Optional.of(generatedRequest()));
        when(orderEmailRepository.findByPortalOrderIdAndRevisionNo(ORDER_ID, 1)).thenReturn(Optional.empty());
        when(excelGenerationService.load("some-file-key.xlsx")).thenReturn(new byte[]{1, 2, 3});

        IdempotentOperation op = new IdempotentOperation("EMAIL_SEND", ORDER_ID.toString(), ORDER_ID + "-1", OffsetDateTime.now());
        op.setId(999L);
        when(idempotencyService.claim(eq("EMAIL_SEND"), eq(ORDER_ID.toString()), eq(ORDER_ID + "-1")))
                .thenReturn(new IdempotencyService.IdempotencyClaim(op, true));

        doThrow(new EmailSendException("SMTP unavailable", new RuntimeException("connection refused")))
                .when(emailSenderPort).send(any(EmailEnvelope.class));

        when(orderEmailRepository.save(any(OrderEmail.class))).thenAnswer(inv -> inv.getArgument(0));

        EmailSendService service = new EmailSendService(portalOrderRepository, orderEmailRepository,
                integrationRequestRepository, auditEventRepository, channelResolutionService, mailPreviewService,
                excelGenerationService, emailSenderPort, idempotencyService, portalUserRepository);

        OrderEmailResponse response = service.send(ORDER_ID, ADMIN);

        assertEquals("FAILED", response.status());
        verify(idempotencyService).markFailed(eq(999L), anyString());
        verify(auditEventRepository).save(argThat(e -> "EMAIL_SEND_FAILED".equals(e.getEventType())));
    }

    // Local helper - avoids pulling in a full ArgumentMatcher import just for
    // this one predicate-style assertion.
    private static com.glv.gsysportal.domain.AuditEvent argThat(java.util.function.Predicate<com.glv.gsysportal.domain.AuditEvent> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
