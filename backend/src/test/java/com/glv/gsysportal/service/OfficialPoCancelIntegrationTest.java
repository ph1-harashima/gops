package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.IntegrationRequestRequiredException;
import com.glv.gsysportal.exception.OfficialPoCancelApprovalNotAllowedException;
import com.glv.gsysportal.exception.OfficialPoCancelNotAllowedException;
import com.glv.gsysportal.exception.OfficialPoCancelReasonRequiredException;
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
 * BR-03 (docs/gulliver-20260917-confirmed-business-rules.md): Official PO
 * Cancel is a two-step Workflow - Cancel Request (reason mandatory) -> ADMIN
 * Approval (-> best-effort "メーカーへ取消連絡") -> CANCELLED. G-OPS-internal
 * Workflow state only (never a Legacy write); Audit Trail records both the
 * Requester and the Approver distinctly.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OfficialPoCancelIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001";
    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin-tester";
    private static final String ADMIN_2 = "admin-tester-2";

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
    void requestCancelRequiresAReason() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN);

        assertThrows(OfficialPoCancelReasonRequiredException.class,
                () -> integrationService.requestCancel(order.getId(), null, ADMIN));
        assertThrows(OfficialPoCancelReasonRequiredException.class,
                () -> integrationService.requestCancel(order.getId(), "   ", ADMIN));
    }

    @Test
    void requestCancelThrowsWhenNoDocumentExistsYet() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());

        assertThrows(IntegrationRequestRequiredException.class,
                () -> integrationService.requestCancel(order.getId(), "Order cancelled by customer", ADMIN));
    }

    @Test
    void requestCancelMovesToCancelRequestedButNotYetCancelled() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest(null, null, null, null, null), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);

        OfficialPoIntegrationResponse requested = integrationService.requestCancel(order.getId(), "Order cancelled by customer", ADMIN);

        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_CANCEL_REQUESTED, requested.lifecycleStatus(),
                "BR-03: a Cancel Request alone must never reach CANCELLED");
        assertEquals("Order cancelled by customer", requested.lifecycleReason());
        // The Integration Status axis (Excel/Import Folder progress) is left
        // untouched by Cancel Request - the two axes never conflate (9章's
        // explicit instruction).
        assertEquals("GENERATED", requested.status());

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        AuditEvent requestEvent = trail.stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_CANCEL_REQUESTED.equals(e.getEventType()))
                .findFirst().orElseThrow();
        assertEquals("Order cancelled by customer", requestEvent.getNote());
        assertEquals(ADMIN, requestEvent.getPerformedBy());
    }

    @Test
    void approveCancelIsRefusedWithoutAPendingRequestFirst() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN);

        assertThrows(OfficialPoCancelApprovalNotAllowedException.class,
                () -> integrationService.approveCancel(order.getId(), ADMIN),
                "approveCancel must never be reachable directly from ACTIVE - a Cancel Request must exist first");
    }

    @Test
    void approveCancelReachesCancelledAndRecordsRequesterAndApproverDistinctly() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest(null, null, null, null, null), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);

        integrationService.requestCancel(order.getId(), "Order cancelled by customer", ADMIN);
        OfficialPoIntegrationResponse cancelled = integrationService.approveCancel(order.getId(), ADMIN_2);

        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_CANCELLED, cancelled.lifecycleStatus());
        assertEquals("Order cancelled by customer", cancelled.lifecycleReason(), "the original Request reason carries through");
        assertEquals("GENERATED", cancelled.status(), "Integration Status axis stays untouched by Cancel");

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        AuditEvent requestEvent = trail.stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_CANCEL_REQUESTED.equals(e.getEventType()))
                .findFirst().orElseThrow();
        AuditEvent approvedEvent = trail.stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_CANCELLED.equals(e.getEventType()))
                .findFirst().orElseThrow();
        AuditEvent notifiedEvent = trail.stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_CANCEL_NOTIFIED.equals(e.getEventType()))
                .findFirst().orElseThrow();
        assertEquals(ADMIN, requestEvent.getPerformedBy(), "who Requested must be distinctly attributable");
        assertEquals(ADMIN_2, approvedEvent.getPerformedBy(), "who Approved must be distinctly attributable, even when different from the Requester");
        // No Manufacturer Contact registered in this test - notification is
        // skipped, but the outcome is always recorded, and Approval still
        // completes regardless (BR-03: notice is never a hard precondition).
        assertEquals("SKIPPED", notifiedEvent.getNewValue());
    }

    @Test
    void cancelledDocumentCannotHaveCancelRequestedOrApprovedAgain() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.requestCancel(order.getId(), "first cancel", ADMIN);
        integrationService.approveCancel(order.getId(), ADMIN);

        assertThrows(OfficialPoCancelNotAllowedException.class,
                () -> integrationService.requestCancel(order.getId(), "second cancel", ADMIN));
        assertThrows(OfficialPoCancelApprovalNotAllowedException.class,
                () -> integrationService.approveCancel(order.getId(), ADMIN));
    }

    @Test
    void reissueRequiredNeverTrueForACancelledDocument() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest(null, null, null, null, null), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);

        integrationService.requestCancel(order.getId(), "cancelled before reissue check", ADMIN);
        OfficialPoIntegrationResponse cancelled = integrationService.approveCancel(order.getId(), ADMIN);

        assertTrue(!cancelled.reissueRequired(), "a CANCELLED Document must never be flagged as needing reissue");
    }
}
