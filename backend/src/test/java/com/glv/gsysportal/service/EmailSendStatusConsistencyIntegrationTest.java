package com.glv.gsysportal.service;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Acceptance Review C-2 fix (docs/gulliver-phase1-acceptance-fix-report.md):
 * "Manufacturer Send" (real Email Send, Phase 9-E) status must never depend
 * on whether/when "Demo Send" (Order Status Transition Service's
 * {@code demoSend}) happened. Before this fix, both {@link
 * EmailSendService#getStatus} and {@link EmailSendService#send} resolved
 * their target Revision via {@link OfficialPoIntegrationService#targetRevisionNo},
 * a PROSPECTIVE "next Revision" number that silently advances the moment
 * {@code demoSend} writes the first Order Revision snapshot - so a real Send
 * recorded against Revision 1 would look "unsent" the moment a later,
 * unrelated Demo Send bumped the target to Revision 2. This suite drives all
 * four orderings the Acceptance Review instruction named (A-D) against the
 * real Service (not a re-implementation), so a regression that reintroduces
 * order-dependence fails here, not just in a manually-driven Browser session.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class EmailSendStatusConsistencyIntegrationTest {

    private static final String SKU = "OD-TENT-001"; // SUP_ALPHA/BR_OUTDOOR
    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin01";

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

    private PortalOrder approvedOrderWithExcelGenerated(String poNo) {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU), null, null, null), OPERATOR);
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
    void scenarioA_demoSendThenManufacturerSend_reportsSent() {
        PortalOrder order = approvedOrderWithExcelGenerated("SEQ-A");
        configureEmailChannelContactAndTemplate();

        statusTransitionService.demoSend(order.getId(), ADMIN);
        OrderEmailResponse sent = emailSendService.send(order.getId(), ADMIN);

        assertEquals("SENT", sent.status());
        assertEquals("SENT", emailSendService.getStatus(order.getId()).status());
    }

    @Test
    void scenarioB_manufacturerSendThenDemoSend_staysSentAfterwards() {
        PortalOrder order = approvedOrderWithExcelGenerated("SEQ-B");
        configureEmailChannelContactAndTemplate();

        OrderEmailResponse sent = emailSendService.send(order.getId(), ADMIN);
        assertEquals("SENT", sent.status());

        // Acceptance Review's exact repro: a Demo Send AFTER a real
        // Manufacturer Send must never make the already-sent Email look
        // unsent.
        statusTransitionService.demoSend(order.getId(), ADMIN);

        OrderEmailResponse status = emailSendService.getStatus(order.getId());
        assertEquals("SENT", status.status(), "a real Send must not be forgotten because of a later Demo Send");

        // A repeat Send call afterward must stay idempotent (the same Send
        // on record, not a new attempt and not an error).
        OrderEmailResponse secondCall = emailSendService.send(order.getId(), ADMIN);
        assertEquals("SENT", secondCall.status());
        assertEquals(sent.sentAt(), secondCall.sentAt(), "must resolve to the SAME Send, not a new one");
    }

    @Test
    void scenarioC_manufacturerSendOnly_reportsSent() {
        PortalOrder order = approvedOrderWithExcelGenerated("SEQ-C");
        configureEmailChannelContactAndTemplate();

        emailSendService.send(order.getId(), ADMIN);

        assertEquals("SENT", emailSendService.getStatus(order.getId()).status());
    }

    @Test
    void scenarioD_demoSendOnly_reportsNotSent() {
        PortalOrder order = approvedOrderWithExcelGenerated("SEQ-D");
        configureEmailChannelContactAndTemplate();

        statusTransitionService.demoSend(order.getId(), ADMIN);

        assertNull(emailSendService.getStatus(order.getId()).status());
    }
}
