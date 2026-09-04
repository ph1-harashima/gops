package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderRevision;
import com.glv.gsysportal.domain.SupplierResponse;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.UpdateDraftRequest;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.InvalidStatusTransitionException;
import com.glv.gsysportal.exception.NoOrderableItemsException;
import com.glv.gsysportal.exception.OrderNotEditableException;
import com.glv.gsysportal.exception.ReturnReasonRequiredException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRevisionRepository;
import com.glv.gsysportal.repository.prototype.SupplierResponseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7-C1 Workflow Transition tests (7-C1 25章): submit-for-approval,
 * approve (plain / with-changes), return-for-correction (reason mandatory),
 * return-to-draft from APPROVED, edit locks per Status/role, PO No.
 * assignment at approval, and Demo Send from APPROVED. Whole-test-method
 * Prototype transaction, always rolled back, so no data is left behind.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OrderStatusTransitionServiceIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // SUP_ALPHA, calc4 recommendedQty = 3
    private static final String SKU_CHAIR_0 = "OD-CHAIR-001"; // SUP_ALPHA, calc4 recommendedQty = 0

    private static final String OPERATOR = "tester01";
    private static final String OTHER_OPERATOR = "tester02";
    private static final String ADMIN = "admin-tester";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private SupplierResponseRepository supplierResponseRepository;
    @Autowired
    private PortalOrderRevisionRepository revisionRepository;
    @Autowired
    private SupplierResponseService supplierResponseService;

    private OrderDraftResponse createDraft(String... skus) {
        return orderDraftService.createDraft(new CreateDraftRequest(List.of(skus), null, null, null), OPERATOR);
    }

    private PortalOrder submitAndApprove(Long id) {
        statusTransitionService.submitForApproval(id, OPERATOR, false);
        return statusTransitionService.approve(id, ADMIN);
    }

    // ---- Submit for approval (7-C1 8章) ----------------------------------

    @Test
    void submitTransitionsDraftToPendingApprovalWithoutPoNo() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        PortalOrder submitted = statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);

        assertEquals(PortalOrder.STATUS_PENDING_APPROVAL, submitted.getStatus());
        assertNull(submitted.getPrototypePoNo(), "PO No. is assigned at approval, never at submission");
        assertEquals(OPERATOR, submitted.getUpdatedBy());
    }

    @Test
    void submitWritesSubmittedForApprovalAndStatusChangedAudit() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(draft.id());
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals(AuditEvent.SUBMITTED_FOR_APPROVAL)));
        assertTrue(events.stream().anyMatch(e ->
                e.getEventType().equals(AuditEvent.STATUS_CHANGED)
                        && "DRAFT".equals(e.getOldValue()) && "PENDING_APPROVAL".equals(e.getNewValue())
        ));
    }

    @Test
    void submitByNonCreatorNonAdminIsDenied() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        assertThrows(AccessDeniedException.class,
                () -> statusTransitionService.submitForApproval(draft.id(), OTHER_OPERATOR, false));
    }

    @Test
    void submitByAdminNonCreatorIsAllowed() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        PortalOrder submitted = statusTransitionService.submitForApproval(draft.id(), ADMIN, true);

        assertEquals(PortalOrder.STATUS_PENDING_APPROVAL, submitted.getStatus());
    }

    @Test
    void submitWithNoOrderableLinesIsRejected() {
        OrderDraftResponse draft = createDraft(SKU_CHAIR_0); // recommendedQty=0 -> initial orderQty=0

        assertThrows(NoOrderableItemsException.class,
                () -> statusTransitionService.submitForApproval(draft.id(), OPERATOR, false));
    }

    @Test
    void duplicateSubmitIsRejected() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);

        assertThrows(InvalidStatusTransitionException.class,
                () -> statusTransitionService.submitForApproval(draft.id(), OPERATOR, false));
    }

    // ---- Approve (7-C1 10章/11章) ----------------------------------------

    @Test
    void approveTransitionsPendingApprovalToApprovedAndAssignsPoNo() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);

        PortalOrder approved = statusTransitionService.approve(draft.id(), ADMIN);

        assertEquals(PortalOrder.STATUS_APPROVED, approved.getStatus());
        assertNotNull(approved.getPrototypePoNo());
        assertTrue(approved.getPrototypePoNo().startsWith("PO-DEMO-"));
        assertEquals(ADMIN, approved.getUpdatedBy());
    }

    @Test
    void plainApproveWritesOrderApprovedAudit() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);

        statusTransitionService.approve(draft.id(), ADMIN);

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(draft.id());
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals(AuditEvent.ORDER_APPROVED)));
        assertTrue(events.stream().noneMatch(e -> e.getEventType().equals(AuditEvent.APPROVED_WITH_CHANGES)));
        assertTrue(events.stream().anyMatch(e ->
                e.getEventType().equals(AuditEvent.STATUS_CHANGED)
                        && "PENDING_APPROVAL".equals(e.getOldValue()) && "APPROVED".equals(e.getNewValue())
        ));
    }

    @Test
    void editAndApproveWritesApprovedWithChangesAndKeepsFieldTrail() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);

        // ADMIN edits the queued Draft (edit-and-approve path, 7-C1 11章).
        Long detailId = draft.details().get(0).id();
        orderDraftService.updateDraft(draft.id(),
                new UpdateDraftRequest(null, null, null,
                        List.of(new UpdateDraftRequest.DetailQtyUpdate(detailId, 7))),
                ADMIN, true);

        statusTransitionService.approve(draft.id(), ADMIN);

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(draft.id());
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals(AuditEvent.APPROVED_WITH_CHANGES)),
                "approving after own edits must record APPROVED_WITH_CHANGES");
        assertTrue(events.stream().noneMatch(e -> e.getEventType().equals(AuditEvent.ORDER_APPROVED)));
        assertTrue(events.stream().anyMatch(e ->
                        e.getEventType().equals(AuditEvent.ORDER_QTY_CHANGED)
                                && ADMIN.equals(e.getPerformedBy())
                                && "3".equals(e.getOldValue()) && "7".equals(e.getNewValue())),
                "the Before/After of the approver's change must be on the trail");
    }

    @Test
    void approveOnDraftOrderIsRejected() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        assertThrows(InvalidStatusTransitionException.class, () -> statusTransitionService.approve(draft.id(), ADMIN));
    }

    @Test
    void approveOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> statusTransitionService.approve(-1L, ADMIN));
    }

    // ---- Return for correction (7-C1 12章) --------------------------------

    @Test
    void returnForCorrectionTransitionsBackToDraftWithReasonInAuditNote() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);

        PortalOrder returned = statusTransitionService.returnForCorrection(draft.id(), "数量を再確認してください", ADMIN);

        assertEquals(PortalOrder.STATUS_DRAFT, returned.getStatus());
        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(draft.id());
        AuditEvent returnEvent = events.stream()
                .filter(e -> e.getEventType().equals(AuditEvent.RETURNED_FOR_CORRECTION))
                .findFirst().orElseThrow();
        assertEquals("数量を再確認してください", returnEvent.getNote());
        assertEquals(ADMIN, returnEvent.getPerformedBy());
        assertNotNull(returnEvent.getPerformedAt());
    }

    @Test
    void returnForCorrectionWithoutReasonIsRejected() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);

        assertThrows(ReturnReasonRequiredException.class,
                () -> statusTransitionService.returnForCorrection(draft.id(), "  ", ADMIN));
    }

    @Test
    void returnedDraftExposesReasonUntilResubmission() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        statusTransitionService.returnForCorrection(draft.id(), "納期を見直してください", ADMIN);

        assertEquals("納期を見直してください", orderDraftService.getDraft(draft.id()).returnReason());

        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        // No longer DRAFT -> no banner.
        assertNull(orderDraftService.getDraft(draft.id()).returnReason());
    }

    @Test
    void operatorCanResubmitAfterReturn() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        statusTransitionService.returnForCorrection(draft.id(), "reason", ADMIN);

        PortalOrder resubmitted = statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        assertEquals(PortalOrder.STATUS_PENDING_APPROVAL, resubmitted.getStatus());
    }

    // ---- Return to Draft from APPROVED + PO No. reuse ---------------------

    @Test
    void returnToDraftFromApprovedPreservesPoNo() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        PortalOrder approved = submitAndApprove(draft.id());
        String poNo = approved.getPrototypePoNo();

        PortalOrder returned = statusTransitionService.returnToDraft(draft.id(), ADMIN);

        assertEquals(PortalOrder.STATUS_DRAFT, returned.getStatus());
        assertEquals(poNo, returned.getPrototypePoNo(), "prototype_po_no must never be cleared or changed by Return to Draft");
    }

    @Test
    void reApprovalAfterReturnToDraftReusesTheSamePoNo() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        PortalOrder firstApproval = submitAndApprove(draft.id());
        String firstPoNo = firstApproval.getPrototypePoNo();

        statusTransitionService.returnToDraft(draft.id(), ADMIN);
        PortalOrder secondApproval = submitAndApprove(draft.id());

        assertEquals(firstPoNo, secondApproval.getPrototypePoNo(),
                "re-approval must reuse the existing PO No., never generate a new one");
    }

    @Test
    void returnToDraftOnNonApprovedOrderIsRejected() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1); // still DRAFT

        assertThrows(InvalidStatusTransitionException.class, () -> statusTransitionService.returnToDraft(draft.id(), ADMIN));
    }

    // ---- Edit locks per Status/role (7-C1 4章/11章) -----------------------

    @Test
    void pendingApprovalDraftIsLockedForOperatorsButEditableByAdmin() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);

        assertThrows(OrderNotEditableException.class, () -> orderDraftService.updateDraft(
                draft.id(), new UpdateDraftRequest(null, null, "operator edit while queued", null), OPERATOR, false));

        OrderDraftResponse adminEdited = orderDraftService.updateDraft(
                draft.id(), new UpdateDraftRequest(null, null, "admin edit while queued", null), ADMIN, true);
        assertEquals("admin edit while queued", adminEdited.remark());
    }

    @Test
    void approvedOrderIsNotEditableEvenByAdmin() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        submitAndApprove(draft.id());

        assertThrows(OrderNotEditableException.class, () -> orderDraftService.updateDraft(
                draft.id(), new UpdateDraftRequest(null, null, "should be rejected", null), ADMIN, true));
    }

    @Test
    void draftEditByNonCreatorOperatorIsDenied() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        assertThrows(AccessDeniedException.class, () -> orderDraftService.updateDraft(
                draft.id(), new UpdateDraftRequest(null, null, "not mine", null), OTHER_OPERATOR, false));
    }

    @Test
    void draftIsEditableAgainAfterReturnToDraft() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        submitAndApprove(draft.id());
        statusTransitionService.returnToDraft(draft.id(), ADMIN);

        OrderDraftResponse updated = orderDraftService.updateDraft(
                draft.id(), new UpdateDraftRequest(null, null, "editable again", null), OPERATOR, false);

        assertEquals("editable again", updated.remark());
    }

    // ---- Demo Send (from APPROVED; implementation instructions 3章/4章/5章) --

    private PortalOrder createApprovedOrder(String... skus) {
        OrderDraftResponse draft = createDraft(skus);
        return submitAndApprove(draft.id());
    }

    @Test
    void demoSendTransitionsApprovedToAwaitingSupplier() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        PortalOrder sent = statusTransitionService.demoSend(approved.getId(), OPERATOR);

        assertEquals(PortalOrder.STATUS_AWAITING_SUPPLIER, sent.getStatus(),
                "user always sees the resting state, never the transient SENT status");
    }

    @Test
    void demoSendWritesSentThenAwaitingSupplierAuditInOrder() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        statusTransitionService.demoSend(approved.getId(), OPERATOR);

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(approved.getId());
        List<AuditEvent> statusChanges = events.stream().filter(e -> e.getEventType().equals(AuditEvent.STATUS_CHANGED)).toList();
        assertEquals(4, statusChanges.size(),
                "DRAFT->PENDING_APPROVAL + PENDING_APPROVAL->APPROVED + APPROVED->SENT + SENT->AWAITING_SUPPLIER");
        assertEquals("APPROVED", statusChanges.get(2).getOldValue());
        assertEquals("SENT", statusChanges.get(2).getNewValue());
        assertEquals("SENT", statusChanges.get(3).getOldValue());
        assertEquals("AWAITING_SUPPLIER", statusChanges.get(3).getNewValue());
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals(AuditEvent.DEMO_SENT)));
    }

    @Test
    void demoSendOnNonApprovedOrderIsRejected() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1); // still DRAFT

        assertThrows(InvalidStatusTransitionException.class, () -> statusTransitionService.demoSend(draft.id(), OPERATOR));
    }

    @Test
    void duplicateDemoSendIsRejectedAndWritesNoExtraAudit() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);
        statusTransitionService.demoSend(approved.getId(), OPERATOR);
        int eventsAfterFirstSend = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(approved.getId()).size();

        assertThrows(InvalidStatusTransitionException.class, () -> statusTransitionService.demoSend(approved.getId(), OTHER_OPERATOR));

        assertEquals(eventsAfterFirstSend, auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(approved.getId()).size(),
                "rejected duplicate Demo Send must not write any Audit rows");
    }

    @Test
    void demoSendInitializesSupplierResponseWithOrderedQtySnapshotAndNullConfirmedQty() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        statusTransitionService.demoSend(approved.getId(), OPERATOR);

        var responses = supplierResponseRepository.findByPortalOrderIdOrderByIdAsc(approved.getId());
        assertEquals(1, responses.size());
        SupplierResponse response = responses.get(0);
        assertEquals(SupplierResponse.STATUS_PARTIAL, response.getResponseStatus());
        assertEquals(1, response.getDetails().size());
        var detail = response.getDetails().get(0);
        assertEquals(3, detail.getOrderedQty(), "ordered_qty snapshot must equal order_qty at Demo Send time");
        assertEquals(null, detail.getConfirmedQty(), "confirmedQty starts unanswered (null), never 0");
    }

    @Test
    void demoSendCreatesRevision1AndLinksSupplierResponseToIt() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        PortalOrder sent = statusTransitionService.demoSend(approved.getId(), OPERATOR);

        assertEquals(1, sent.getCurrentRevisionNo(), "first ever Send must crystallize Revision 1");
        var revision = revisionRepository.findByPortalOrderIdAndRevisionNo(sent.getId(), 1).orElseThrow();
        assertEquals(PortalOrderRevision.TYPE_INITIAL, revision.getRevisionType());
        assertEquals(null, revision.getReason(), "INITIAL Revision never carries a reason");
        assertEquals(1, revision.getDetails().size());
        assertEquals(SKU_TENT_1, revision.getDetails().get(0).getSkuCode());
        assertEquals(3, revision.getDetails().get(0).getOrderedQty());

        SupplierResponse response = supplierResponseRepository
                .findByPortalOrderIdAndOrderRevisionId(sent.getId(), revision.getId()).orElseThrow();
        assertEquals(response.getId(), supplierResponseRepository.findByPortalOrderIdOrderByIdAsc(sent.getId()).get(0).getId());
    }

    @Test
    void demoSendOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> statusTransitionService.demoSend(-1L, OPERATOR));
    }

    // ---- Phase 7-H: EDI発注Workflow Foundation ---------------------------

    @Test
    void demoSendRecordsEmailAsTheCommunicationChannel() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        PortalOrder sent = statusTransitionService.demoSend(approved.getId(), OPERATOR);

        assertEquals(PortalOrder.CHANNEL_EMAIL, sent.getCommunicationChannel());
    }

    @Test
    void recordEdiSendTransitionsApprovedToAwaitingSupplierSameAsDemoSend() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        PortalOrder sent = statusTransitionService.recordEdiSend(approved.getId(), ADMIN);

        assertEquals(PortalOrder.STATUS_AWAITING_SUPPLIER, sent.getStatus());
        assertEquals(PortalOrder.CHANNEL_EDI, sent.getCommunicationChannel());
    }

    @Test
    void recordEdiSendWritesEdiSendRecordedAuditNotDemoSent() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        statusTransitionService.recordEdiSend(approved.getId(), ADMIN);

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(approved.getId());
        assertTrue(events.stream().anyMatch(e -> AuditEvent.EDI_SEND_RECORDED.equals(e.getEventType())));
        assertTrue(events.stream().noneMatch(e -> AuditEvent.DEMO_SENT.equals(e.getEventType())),
                "an EDI Send must never also claim a Demo (Email) Send happened");
    }

    /** The central Foundation requirement (Phase 7-H 7章/10章): a Supplier
     * ordered from over EDI, who is never Email-sent via demoSend, must
     * still be able to reach Supplier Response - confirmed via the actual
     * Service call, not just repository state, since that Service is what
     * the real Frontend screen depends on. */
    @Test
    void recordEdiSendMakesSupplierResponseReachableWithoutAnyDemoSend() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        PortalOrder sent = statusTransitionService.recordEdiSend(approved.getId(), ADMIN);

        var response = supplierResponseService.getSupplierResponse(sent.getId());
        assertEquals(1, response.details().size());
        assertEquals(null, response.details().get(0).confirmedQty(), "unanswered, same as the Email path");

        assertEquals(1, sent.getCurrentRevisionNo(), "EDI Send must crystallize a Revision exactly like Demo Send does");
        var revision = revisionRepository.findByPortalOrderIdAndRevisionNo(sent.getId(), 1).orElseThrow();
        assertEquals(PortalOrderRevision.TYPE_INITIAL, revision.getRevisionType());
    }

    @Test
    void recordEdiSendOnNonApprovedOrderIsRejected() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        assertThrows(InvalidStatusTransitionException.class,
                () -> statusTransitionService.recordEdiSend(draft.id(), ADMIN));
    }

    @Test
    void recordEdiSendOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> statusTransitionService.recordEdiSend(-1L, ADMIN));
    }

    // --- Phase 9-D: Email / EDI branching - completeEdiInput ---

    @Test
    void recordEdiSendSetsWaitingInputEdiStatus() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        PortalOrder sent = statusTransitionService.recordEdiSend(approved.getId(), ADMIN);

        assertEquals(PortalOrder.EDI_STATUS_WAITING_INPUT, sent.getEdiStatus());
    }

    @Test
    void demoSendNeverSetsEdiStatus() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        PortalOrder sent = statusTransitionService.demoSend(approved.getId(), OPERATOR);

        assertNull(sent.getEdiStatus());
    }

    @Test
    void completeEdiInputTransitionsWaitingToCompleted() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);
        statusTransitionService.recordEdiSend(approved.getId(), ADMIN);

        PortalOrder completed = statusTransitionService.completeEdiInput(approved.getId(), OPERATOR);

        assertEquals(PortalOrder.EDI_STATUS_COMPLETED, completed.getEdiStatus());
        assertEquals(OPERATOR, completed.getEdiCompletedBy());
        assertNotNull(completed.getEdiCompletedAt());

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(approved.getId());
        assertTrue(events.stream().anyMatch(e -> AuditEvent.EDI_INPUT_COMPLETED.equals(e.getEventType())));
    }

    @Test
    void completeEdiInputOnEmailChannelOrderIsRejected() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);
        statusTransitionService.demoSend(approved.getId(), OPERATOR);

        assertThrows(com.glv.gsysportal.exception.EdiCompletionNotApplicableException.class,
                () -> statusTransitionService.completeEdiInput(approved.getId(), ADMIN));
    }

    @Test
    void completeEdiInputBeforeAnySendIsRejected() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);

        assertThrows(com.glv.gsysportal.exception.EdiCompletionNotApplicableException.class,
                () -> statusTransitionService.completeEdiInput(approved.getId(), ADMIN));
    }

    @Test
    void completeEdiInputTwiceIsIdempotent_noDoubleAudit() {
        PortalOrder approved = createApprovedOrder(SKU_TENT_1);
        statusTransitionService.recordEdiSend(approved.getId(), ADMIN);

        statusTransitionService.completeEdiInput(approved.getId(), OPERATOR);
        statusTransitionService.completeEdiInput(approved.getId(), OPERATOR);

        long completedCount = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(approved.getId()).stream()
                .filter(e -> AuditEvent.EDI_INPUT_COMPLETED.equals(e.getEventType())).count();
        assertEquals(1, completedCount, "re-completing an already-COMPLETED Order must not re-Audit");
    }
}
