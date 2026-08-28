package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.OrderNotApprovedException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7-C2A: Integration Request Test + Idempotency Test + State Model
 * regression (24章). Whole-test-method Prototype transaction, always rolled
 * back (matches OrderStatusTransitionServiceIntegrationTest's convention).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OfficialPoIntegrationServiceIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // SUP_ALPHA/BR_OUTDOOR
    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin-tester";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private OfficialPoIntegrationRequestRepository integrationRequestRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private PortalOrder createApprovedOrder() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        return statusTransitionService.approve(draft.id(), ADMIN);
    }

    @Test
    void getIntegrationOnAnOrderWithNoRequestReturnsNotRequested() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);

        OfficialPoIntegrationResponse response = integrationService.getIntegration(draft.id());

        assertEquals(OfficialPoIntegrationResponse.STATUS_NOT_REQUESTED, response.status());
        assertNull(response.officialPoNo());
        assertNull(response.preflight());
    }

    @Test
    void getIntegrationOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> integrationService.getIntegration(999_999L));
    }

    @Test
    void requestOnNonApprovedOrderIsRejected() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);

        assertThrows(OrderNotApprovedException.class, () -> integrationService.requestIntegration(draft.id(), ADMIN));

        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false); // now PENDING_APPROVAL
        assertThrows(OrderNotApprovedException.class, () -> integrationService.requestIntegration(draft.id(), ADMIN));
    }

    @Test
    void requestOnApprovedOrderCreatesPendingRequestWithNullOfficialPoNo() {
        PortalOrder order = createApprovedOrder();

        OfficialPoIntegrationResponse response = integrationService.requestIntegration(order.getId(), ADMIN);

        assertEquals("PENDING", response.status());
        assertNull(response.officialPoNo(), "7-C2A 7章's Gate: no auto-numbering this Phase");
        assertEquals(1, response.revisionNo());
        assertEquals(ADMIN, response.requestedBy());
        assertNotNull(response.preflight());
    }

    @Test
    void doubleRequestIsIdempotent_noDuplicateRowCreated() {
        PortalOrder order = createApprovedOrder();

        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN); // second click

        long rowCount = integrationRequestRepository.findAll().stream()
                .filter(r -> order.getId().equals(r.getPortalOrderId())).count();
        assertEquals(1, rowCount, "portalOrderId+revisionNo must stay a single row (7-C2A 4章)");
    }

    @Test
    void auditRequestedEventWrittenOnceNotOnRepeatCalls() {
        PortalOrder order = createApprovedOrder();

        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN);

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        long requestedCount = trail.stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_INTEGRATION_REQUESTED.equals(e.getEventType())).count();
        long precheckCount = trail.stream()
                .filter(e -> AuditEvent.PRECHECK_COMPLETED.equals(e.getEventType())).count();
        assertEquals(1, requestedCount, "OFFICIAL_PO_INTEGRATION_REQUESTED only on first creation (7-C2A 19章)");
        assertEquals(3, precheckCount, "PRECHECK_COMPLETED on every Preflight run, including repeats");
    }

    @Test
    void knownGoodOrderPreflightsAsPass() {
        PortalOrder order = createApprovedOrder();

        OfficialPoIntegrationResponse response = integrationService.requestIntegration(order.getId(), ADMIN);

        assertEquals("PASS", response.preflight().result());
        assertTrue(response.preflight().issues().stream().anyMatch(i -> "OFFICIAL_PO_NO_NOT_ASSIGNED".equals(i.code())));
    }

    @Test
    void requestOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> integrationService.requestIntegration(999_999L, ADMIN));
    }
}
