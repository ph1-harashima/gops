package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.OfficialPoCancelNotAllowedException;
import com.glv.gsysportal.exception.OfficialPoCancelReasonRequiredException;
import com.glv.gsysportal.exception.IntegrationRequestRequiredException;
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
 * Gap Analysis C-4 (docs/gulliver-20260917-phase1-gap-analysis.md 9章):
 * Official PO Cancel - G-OPS-internal Workflow state only (never a Legacy
 * write), reason mandatory, Audit Trail records who/when/what/why.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OfficialPoCancelIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001";
    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin-tester";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private PortalOrder approveViaWorkflow(Long orderId) {
        statusTransitionService.submitForApproval(orderId, OPERATOR, false);
        return statusTransitionService.approve(orderId, ADMIN);
    }

    @Test
    void cancelRequiresAReason() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN);

        assertThrows(OfficialPoCancelReasonRequiredException.class,
                () -> integrationService.cancel(order.getId(), null, ADMIN));
        assertThrows(OfficialPoCancelReasonRequiredException.class,
                () -> integrationService.cancel(order.getId(), "   ", ADMIN));
    }

    @Test
    void cancelThrowsWhenNoDocumentExistsYet() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());

        assertThrows(IntegrationRequestRequiredException.class,
                () -> integrationService.cancel(order.getId(), "Order cancelled by customer", ADMIN));
    }

    @Test
    void cancelSucceedsAndRecordsAuditTrail() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest("PO-CANCEL-" + draft.id(), null, null, null, null, null), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);

        OfficialPoIntegrationResponse cancelled = integrationService.cancel(order.getId(), "Order cancelled by customer", ADMIN);

        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_CANCELLED, cancelled.lifecycleStatus());
        assertEquals("Order cancelled by customer", cancelled.lifecycleReason());
        // The Integration Status axis (Excel/Import Folder progress) is left
        // untouched by Cancel - the two axes never conflate (9章's explicit
        // instruction).
        assertEquals("GENERATED", cancelled.status());

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        AuditEvent cancelEvent = trail.stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_CANCELLED.equals(e.getEventType()))
                .findFirst().orElseThrow();
        assertEquals("Order cancelled by customer", cancelEvent.getNote());
        assertEquals(ADMIN, cancelEvent.getPerformedBy());
    }

    @Test
    void cancelledDocumentCannotBeCancelledAgain() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.cancel(order.getId(), "first cancel", ADMIN);

        assertThrows(OfficialPoCancelNotAllowedException.class,
                () -> integrationService.cancel(order.getId(), "second cancel", ADMIN));
    }

    @Test
    void reissueRequiredNeverTrueForACancelledDocument() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest("PO-CANCEL2-" + draft.id(), null, null, null, null, null), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);

        OfficialPoIntegrationResponse cancelled = integrationService.cancel(order.getId(), "cancelled before reissue check", ADMIN);

        assertTrue(!cancelled.reissueRequired(), "a CANCELLED Document must never be flagged as needing reissue");
    }
}
