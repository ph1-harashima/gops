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
    void requestOnApprovedOrderCreatesPendingRequestWithAutoAssignedOfficialPoNo() {
        PortalOrder order = createApprovedOrder();

        OfficialPoIntegrationResponse response = integrationService.requestIntegration(order.getId(), ADMIN);

        assertEquals("PENDING", response.status());
        // BR-08 (docs/gulliver-20260917-confirmed-business-rules.md):
        // superseded 7-C2A 7章's original "no auto-numbering this Phase" Gate -
        // the Official PO No. is now auto-numbered immediately.
        assertNotNull(response.officialPoNo());
        assertTrue(response.officialPoNo().matches("^#[A-Z]{3}-[A-Z]{3}\\d{3}$"));
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

    /** Phase 7-C5 16章: after a Send + correction + re-Approval cycle, the
     * NEXT Integration Request must target Revision 2 (currentRevisionNo=1
     * from the first Send, +1 for the upcoming re-Send), never stay pinned at
     * 1 - Integration Revision and Order Revision are the SAME concept. */
    @Test
    void requestAfterCorrectionCycleTargetsNextRevisionNo() {
        PortalOrder order = createApprovedOrder();
        PortalOrder sent = statusTransitionService.demoSend(order.getId(), ADMIN);
        assertEquals(1, sent.getCurrentRevisionNo());

        var response = supplierResponseService.getSupplierResponse(sent.getId());
        supplierResponseService.saveSupplierResponse(sent.getId(), new com.glv.gsysportal.dto.request.SaveSupplierResponseRequest(
                null, null, List.of(new com.glv.gsysportal.dto.request.SaveSupplierResponseRequest.LineUpdate(
                        response.details().get(0).detailId(), 1, null, null, null))), ADMIN);
        supplierResponseService.confirmSupplierResponse(sent.getId(), ADMIN);
        orderRevisionService.createCorrection(sent.getId(), new com.glv.gsysportal.dto.request.CreateRevisionRequest("fix", true), ADMIN);
        statusTransitionService.submitForApproval(sent.getId(), ADMIN, true);
        statusTransitionService.approve(sent.getId(), ADMIN); // APPROVED again, still currentRevisionNo=1 (not yet re-sent)

        OfficialPoIntegrationResponse integration = integrationService.requestIntegration(sent.getId(), ADMIN);

        assertEquals(2, integration.revisionNo(), "targets the revision this APPROVED Order will become on its NEXT Send");
    }

    @Autowired
    private SupplierResponseService supplierResponseService;
    @Autowired
    private OrderRevisionService orderRevisionService;
}
