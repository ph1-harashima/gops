package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.ManufacturerChannel;
import com.glv.gsysportal.domain.MailTemplate;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.MailTemplateRequest;
import com.glv.gsysportal.dto.request.ManufacturerChannelRequest;
import com.glv.gsysportal.dto.request.SupplierContactRequest;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.dto.response.OrderEmailResponse;
import com.glv.gsysportal.exception.EmailAttachmentNotReadyException;
import com.glv.gsysportal.exception.EmailChannelNotApplicableException;
import com.glv.gsysportal.exception.EmailPreviewBlockedException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 9-E: Real Email Send - success path via the real
 * {@code LoggingEmailSenderAdapter} (active under the "test" profile,
 * matching every environment this Phase can run in - see that Adapter's own
 * Javadoc for why the failure/retry path is instead covered by
 * {@link EmailSendServiceRetryTest}, a pure Mockito unit test that mocks
 * {@code EmailSenderPort} directly).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class EmailSendServiceIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // SUP_ALPHA/BR_OUTDOOR
    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin01"; // real seeded portal_user - MailPreviewService needs it

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private EmailSendService emailSendService;
    @Autowired
    private SupplierContactService contactService;
    @Autowired
    private MailTemplateService templateService;
    @Autowired
    private ManufacturerChannelService channelService;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private PortalOrder readyOrderWithExcelGenerated(String poNo) {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest(poNo, "WK36", "2026-09-05", null, null, null), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);
        return order;
    }

    private void configureEmailChannelContactAndTemplate() {
        channelService.create(new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EMAIL, true), ADMIN);
        contactService.create(new SupplierContactRequest("SUP_ALPHA", "BR_OUTDOOR", "Taro Yamada", "taro@example.com",
                SupplierContact.CONTACT_TYPE_TO, SupplierContact.LANGUAGE_JA, null, null, true, true), ADMIN);
        templateService.create(new MailTemplateRequest("PO Template", MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER,
                "SUP_ALPHA", "BR_OUTDOOR", SupplierContact.LANGUAGE_JA,
                "PO {{poNo}}", "Dear {{contactName}}, PO No: {{poNo}}", "OFFICIAL_PO_EXCEL", true), ADMIN);
    }

    @Test
    void sendRequiresEmailChannel() {
        PortalOrder order = readyOrderWithExcelGenerated("EMAIL-TEST-1");
        channelService.create(new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EDI, true), ADMIN);

        assertThrows(EmailChannelNotApplicableException.class, () -> emailSendService.send(order.getId(), ADMIN));
    }

    @Test
    void sendRequiresUnblockedPreview() {
        PortalOrder order = readyOrderWithExcelGenerated("EMAIL-TEST-2");
        channelService.create(new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EMAIL, true), ADMIN);
        // No SupplierContact/MailTemplate configured - Preview stays BLOCKED.

        assertThrows(EmailPreviewBlockedException.class, () -> emailSendService.send(order.getId(), ADMIN));
    }

    @Test
    void sendRequiresGeneratedExcel() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest("EMAIL-TEST-3", "WK36", "2026-09-05", null, null, null), ADMIN);
        // Excel never generated.
        configureEmailChannelContactAndTemplate();

        assertThrows(EmailAttachmentNotReadyException.class, () -> emailSendService.send(order.getId(), ADMIN));
    }

    @Test
    void sendSucceedsAndRecordsAudit() {
        PortalOrder order = readyOrderWithExcelGenerated("EMAIL-TEST-4");
        configureEmailChannelContactAndTemplate();

        OrderEmailResponse response = emailSendService.send(order.getId(), ADMIN);

        assertEquals("SENT", response.status());
        assertEquals(List.of("taro@example.com"), response.to());
        assertEquals("PO EMAIL-TEST-4", response.subject());
        // Display Name統一 (audit finding: "送信済みです (admin01 - ...)" showed
        // the raw Login ID) - same idiom as AuditEventView.performedByDisplayName.
        assertEquals(ADMIN, response.sentBy());
        assertTrue(response.sentByDisplayName() != null && !response.sentByDisplayName().equals(ADMIN));

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        assertTrue(trail.stream().anyMatch(e -> AuditEvent.EMAIL_SENT.equals(e.getEventType())));
    }

    @Test
    void doubleSendIsIdempotent_noDoubleAudit() {
        PortalOrder order = readyOrderWithExcelGenerated("EMAIL-TEST-5");
        configureEmailChannelContactAndTemplate();

        emailSendService.send(order.getId(), ADMIN);
        OrderEmailResponse second = emailSendService.send(order.getId(), ADMIN);

        assertEquals("SENT", second.status());
        long sentAuditCount = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .filter(e -> AuditEvent.EMAIL_SENT.equals(e.getEventType())).count();
        assertEquals(1, sentAuditCount, "double-send must not re-send/re-audit");
    }

    @Test
    void getStatusBeforeAnySendReturnsNotSent() {
        PortalOrder order = readyOrderWithExcelGenerated("EMAIL-TEST-6");

        OrderEmailResponse status = emailSendService.getStatus(order.getId());

        assertEquals(null, status.status());
    }
}
