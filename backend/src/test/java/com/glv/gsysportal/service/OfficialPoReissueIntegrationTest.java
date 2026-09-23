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
import com.glv.gsysportal.exception.OrderNotApprovedException;
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

    /** BR-08: the Official PO No. is auto-numbered by requestIntegration
     * itself now - returns whatever value was actually assigned. */
    private String issueOfficialPo(Long orderId) {
        integrationService.requestIntegration(orderId, ADMIN);
        integrationService.confirmOfficialPoNumber(orderId,
                new ConfirmOfficialPoNumberRequest("WK36", "2026-09-05", null, null, null), ADMIN);
        integrationService.generateExcel(orderId, ADMIN);
        return integrationService.getIntegration(orderId).officialPoNo();
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
        issueOfficialPo(order.getId());

        // Excel was generated (issued), but no ORDER_REVISION_CREATED
        // correction event exists yet - reissue must refuse.
        assertThrows(OfficialPoReissueNotRequiredException.class,
                () -> integrationService.reissue(order.getId(), ADMIN));
    }

    /** BR-02 (docs/gulliver-20260917-confirmed-business-rules.md): "変更 →
     * 再承認 → Reissue → 再送" is the required order - a correction alone
     * (Order still DRAFT/PENDING_APPROVAL, not yet re-approved) must never
     * let Reissue create Revision 2. This is enforced structurally: the only
     * way to change Ordered Qty after issuance is
     * {@code OrderRevisionService.createCorrection} (SUPPLIER_CONFIRMED ->
     * DRAFT), and {@code reissue()} itself requires {@code order.status ==
     * APPROVED} - the sole path back to APPROVED is submitForApproval ->
     * ADMIN approve, so re-approval can never be skipped. */
    @Test
    void reissueIsRefusedBeforeReapproval_evenAfterAGenuineCorrection() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        issueOfficialPo(order.getId());

        PortalOrder sent = statusTransitionService.demoSend(order.getId(), ADMIN);
        Long detailId = supplierResponseService.getSupplierResponse(sent.getId()).details().get(0).detailId();
        supplierResponseService.saveSupplierResponse(sent.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(new SaveSupplierResponseRequest.LineUpdate(detailId, 2, null, null, null))),
                ADMIN);
        supplierResponseService.confirmSupplierResponse(sent.getId(), ADMIN);
        orderRevisionService.createCorrection(sent.getId(),
                new CreateRevisionRequest("Supplier can only supply 2", true), ADMIN);
        // Order is now back in DRAFT - the correction happened, but there
        // has been NO re-approval yet.

        assertThrows(OrderNotApprovedException.class,
                () -> integrationService.reissue(sent.getId(), ADMIN),
                "Reissue must be refused until the corrected Order is re-approved (submit-for-approval + ADMIN approve)");

        // Submitting for approval alone (PENDING_APPROVAL, still not
        // APPROVED) must also not be enough.
        statusTransitionService.submitForApproval(sent.getId(), OPERATOR, false);
        assertThrows(OrderNotApprovedException.class,
                () -> integrationService.reissue(sent.getId(), ADMIN),
                "Reissue must be refused while only PENDING_APPROVAL - ADMIN approval itself must have happened");

        // Only after the ADMIN's explicit approval does Reissue become
        // possible at all.
        PortalOrder reapproved = statusTransitionService.approve(sent.getId(), ADMIN);
        OfficialPoIntegrationResponse reissued = integrationService.reissue(reapproved.getId(), ADMIN);
        assertEquals(2, reissued.revisionNo());
    }

    @Test
    void fullReissueCycle_oldSupersededNewActiveRevisionHistoryComplete() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        PortalOrder order = approveViaWorkflow(draft.id());
        String poNo = issueOfficialPo(order.getId());
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
        // BR-08 Scenario 7: Reissue advances the Revision, never the
        // Official PO No. itself - #ABC-XYZ001/Revision 002, never #ABC-XYZ002.
        assertEquals(poNo, reissued.officialPoNo(), "Reissue must carry the SAME Official PO No. forward, never re-number it");

        List<OfficialPoRevisionHistoryEntry> history = integrationService.getRevisionHistory(reapproved.getId());
        assertEquals(2, history.size());
        assertEquals(1, history.get(0).revisionNo());
        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_SUPERSEDED, history.get(0).lifecycleStatus());
        assertEquals(poNo, history.get(0).officialPoNo(), "old Revision's PO No. is preserved, never deleted");
        assertEquals(2, history.get(1).revisionNo());
        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_ACTIVE, history.get(1).lifecycleStatus());
        assertEquals(poNo, history.get(1).officialPoNo(), "BR-08: the new Revision's own Official PO No. must be identical to Revision 1's");

        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(reapproved.getId()).stream()
                .anyMatch(e -> AuditEvent.OFFICIAL_PO_REISSUED.equals(e.getEventType())));

        // Reissuing again immediately must refuse (nothing issued yet for
        // the new ACTIVE Revision 2 Document).
        assertThrows(OfficialPoReissueNotRequiredException.class,
                () -> integrationService.reissue(reapproved.getId(), ADMIN));
    }
}
