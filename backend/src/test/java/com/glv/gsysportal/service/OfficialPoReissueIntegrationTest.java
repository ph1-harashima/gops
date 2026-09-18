package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.CreateRevisionRequest;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OfficialPoRevisionHistoryEntry;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.IntegrationRequestRequiredException;
import com.glv.gsysportal.exception.OfficialPoReissueNotRequiredException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gap Analysis C-2/C-3 (docs/gulliver-20260917-phase1-gap-analysis.md 7章/8章):
 * Official PO Reissue - "既存Revisionモデルを利用" (never a new Qty-comparison
 * Business Rule), Revision History, and the isReissueRequired detection
 * shared between the "at a glance" banner and the Reissue Action's own Gate.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OfficialPoReissueIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001";
    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin-tester";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private SupplierResponseService supplierResponseService;
    @Autowired
    private OrderRevisionService orderRevisionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private PortalOrder approveViaWorkflow(Long orderId) {
        statusTransitionService.submitForApproval(orderId, OPERATOR, false);
        return statusTransitionService.approve(orderId, ADMIN);
    }

    private PortalOrder issueOfficialPo(Long orderId, String poNo) {
        integrationService.requestIntegration(orderId, ADMIN);
        integrationService.confirmOfficialPoNumber(orderId,
                new ConfirmOfficialPoNumberRequest(poNo, "WK36", "2026-09-05", null, null, null), ADMIN);
        integrationService.generateExcel(orderId, ADMIN);
        return null;
    }

    @Test
    void reissueThrowsWhenNothingWasEverIssued() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        integrationService.requestIntegration(order.getId(), ADMIN); // PENDING only, never generated

        assertThrows(OfficialPoReissueNotRequiredException.class,
                () -> integrationService.reissue(order.getId(), ADMIN));
    }

    @Test
    void reissueThrowsWhenNoRequestExistsYet() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());

        assertThrows(IntegrationRequestRequiredException.class,
                () -> integrationService.reissue(order.getId(), ADMIN));
    }

    @Test
    void reissueThrowsWhenIssuedButNoCorrectionHappenedSince() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        issueOfficialPo(order.getId(), "PO-REISSUE-A-" + draft.id());

        // Excel was generated (issued), but no ORDER_REVISION_CREATED
        // correction event exists yet - reissue must refuse.
        assertThrows(OfficialPoReissueNotRequiredException.class,
                () -> integrationService.reissue(order.getId(), ADMIN));
    }

    @Test
    void fullReissueCycle_oldSupersededNewActiveRevisionHistoryComplete() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        String poNo = "PO-REISSUE-B-" + draft.id();
        issueOfficialPo(order.getId(), poNo);
        integrationService.placeToImportFolder(order.getId(), ADMIN); // reach SUBMITTED

        OfficialPoIntegrationResponse beforeCorrection = integrationService.getIntegration(order.getId());
        assertFalse(beforeCorrection.reissueRequired(), "no correction has happened yet");

        // Send, get a Supplier Response with a different Qty, correct the Order.
        PortalOrder sent = statusTransitionService.demoSend(order.getId(), ADMIN);
        Long detailId = supplierResponseService.getSupplierResponse(sent.getId()).details().get(0).detailId();
        supplierResponseService.saveSupplierResponse(sent.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(new SaveSupplierResponseRequest.LineUpdate(detailId, 2, null, null, null))),
                ADMIN);
        supplierResponseService.confirmSupplierResponse(sent.getId(), ADMIN);
        orderRevisionService.createCorrection(sent.getId(),
                new CreateRevisionRequest("Supplier can only supply 2", true), ADMIN);
        // Re-approve the correction (still targets Revision 2 - crystallizes
        // only at the NEXT Send, which Reissue does not require).
        statusTransitionService.submitForApproval(sent.getId(), OPERATOR, false);
        PortalOrder reapproved = statusTransitionService.approve(sent.getId(), ADMIN);

        OfficialPoIntegrationResponse afterCorrection = integrationService.getIntegration(reapproved.getId());
        assertTrue(afterCorrection.reissueRequired(), "a correction happened after this Document was issued");

        OfficialPoIntegrationResponse reissued = integrationService.reissue(reapproved.getId(), ADMIN);

        assertEquals(2, reissued.revisionNo());
        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_ACTIVE, reissued.lifecycleStatus());
        assertEquals("PENDING", reissued.status());
        assertFalse(reissued.reissueRequired(), "the new ACTIVE Document has not been issued yet");

        List<OfficialPoRevisionHistoryEntry> history = integrationService.getRevisionHistory(reapproved.getId());
        assertEquals(2, history.size());
        assertEquals(1, history.get(0).revisionNo());
        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_SUPERSEDED, history.get(0).lifecycleStatus());
        assertEquals(poNo, history.get(0).officialPoNo(), "old Revision's PO No. is preserved, never deleted");
        assertEquals(2, history.get(1).revisionNo());
        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_ACTIVE, history.get(1).lifecycleStatus());

        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(reapproved.getId()).stream()
                .anyMatch(e -> AuditEvent.OFFICIAL_PO_REISSUED.equals(e.getEventType())));

        // Reissuing again immediately must refuse (nothing issued yet for
        // the new ACTIVE Revision 2 Document).
        assertThrows(OfficialPoReissueNotRequiredException.class,
                () -> integrationService.reissue(reapproved.getId(), ADMIN));
    }
}
