package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderRevision;
import com.glv.gsysportal.domain.SupplierResponse;
import com.glv.gsysportal.dto.request.AgreeResponseRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.CreateRevisionRequest;
import com.glv.gsysportal.dto.request.ReopenAgreementRequest;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.dto.response.OrderRevisionSummary;
import com.glv.gsysportal.dto.response.ResponseDifferenceView;
import com.glv.gsysportal.dto.response.SupplierResponseHistoryEntry;
import com.glv.gsysportal.dto.response.SupplierResponseView;
import com.glv.gsysportal.exception.OrderNotAgreedException;
import com.glv.gsysportal.exception.RevisionCreationNotAllowedException;
import com.glv.gsysportal.exception.RevisionReasonRequiredException;
import com.glv.gsysportal.exception.ReopenReasonRequiredException;
import com.glv.gsysportal.exception.ResponseNotAgreeableException;
import com.glv.gsysportal.exception.UnacknowledgedAttentionException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRevisionRepository;
import com.glv.gsysportal.repository.prototype.SupplierResponseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7-C5 27章 Heavy Regression test list: Revision creation, Revision
 * immutability, Response-Revision link, Difference Detection, Agreement,
 * Reopen, Permission (Service-level; Controller-level 403 is covered
 * separately by {@code SupplierResponseRevisionApiTest}). Same
 * whole-test-method Prototype transaction + rollback pattern as the other
 * Supplier Response tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OrderRevisionWorkflowIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // recommendedQty=3
    private static final String ADMIN = "admin01";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private SupplierResponseService supplierResponseService;
    @Autowired
    private OrderRevisionService orderRevisionService;
    @Autowired
    private AttentionService attentionService;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private OrderAttentionRepository orderAttentionRepository;
    @Autowired
    private PortalOrderRevisionRepository revisionRepository;
    @Autowired
    private SupplierResponseRepository supplierResponseRepository;

    private PortalOrder approveViaWorkflow(Long orderId) {
        statusTransitionService.submitForApproval(orderId, ADMIN, true);
        return statusTransitionService.approve(orderId, ADMIN);
    }

    private PortalOrder createAwaitingSupplierOrder(String... skus) {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(skus), null, null, null), ADMIN);
        PortalOrder ready = approveViaWorkflow(draft.id());
        return statusTransitionService.demoSend(ready.getId(), ADMIN);
    }

    /** AWAITING_SUPPLIER -> SUPPLIER_CONFIRMED with every line answered
     * confirmedQty=confirmedQty. */
    private PortalOrder confirmWithQty(Long orderId, int qty) {
        SupplierResponseView view = supplierResponseService.getSupplierResponse(orderId);
        Long detailId = view.details().get(0).detailId();
        supplierResponseService.saveSupplierResponse(orderId, new SaveSupplierResponseRequest(null, null,
                List.of(new SaveSupplierResponseRequest.LineUpdate(detailId, qty, null, null, null))), ADMIN);
        return supplierResponseService.confirmSupplierResponse(orderId, ADMIN);
    }

    // --- Revision creation / immutability ---

    @Test
    void createRevisionMovesOrderBackToDraftAndWritesAuditWithReason() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        confirmWithQty(order.getId(), 2); // difference vs orderedQty=3

        PortalOrder corrected = orderRevisionService.createCorrection(order.getId(),
                new CreateRevisionRequest("Supplier can only supply 2", false), ADMIN);

        assertEquals(PortalOrder.STATUS_DRAFT, corrected.getStatus());
        assertEquals(1, corrected.getCurrentRevisionNo(), "currentRevisionNo only advances at the NEXT Send, not at creation");
        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        AuditEvent created = events.stream().filter(e -> e.getEventType().equals(AuditEvent.ORDER_REVISION_CREATED)).findFirst().orElseThrow();
        assertEquals("Supplier can only supply 2", created.getNote());
    }

    @Test
    void createRevisionWithoutReasonIsRejected() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        confirmWithQty(order.getId(), 2);

        assertThrows(RevisionReasonRequiredException.class, () -> orderRevisionService.createCorrection(order.getId(),
                new CreateRevisionRequest(" ", false), ADMIN));
    }

    @Test
    void createRevisionOutsideSupplierConfirmedIsRejected() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1); // AWAITING_SUPPLIER, not yet confirmed

        assertThrows(RevisionCreationNotAllowedException.class, () -> orderRevisionService.createCorrection(order.getId(),
                new CreateRevisionRequest("reason", false), ADMIN));
    }

    @Test
    void createRevisionWithApplyConfirmedValuesUpdatesLiveOrderQtyAndAudits() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1); // orderedQty=3
        confirmWithQty(order.getId(), 2);

        orderRevisionService.createCorrection(order.getId(),
                new CreateRevisionRequest("apply confirmed", true), ADMIN);

        OrderDraftResponse draft = orderDraftService.getDraft(order.getId());
        assertEquals(2, draft.details().get(0).orderQty(), "applyConfirmedValues must overwrite the live orderQty");
        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .anyMatch(e -> e.getEventType().equals(AuditEvent.ORDER_QTY_CHANGED) && "3".equals(e.getOldValue()) && "2".equals(e.getNewValue())),
                "applying confirmed values reuses the existing ORDER_QTY_CHANGED event, not a new one");
    }

    @Test
    void createRevisionWithoutApplyConfirmedValuesLeavesLiveOrderQtyUnchanged() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1); // orderedQty=3
        confirmWithQty(order.getId(), 2);

        orderRevisionService.createCorrection(order.getId(),
                new CreateRevisionRequest("manual edit instead", false), ADMIN);

        OrderDraftResponse draft = orderDraftService.getDraft(order.getId());
        assertEquals(3, draft.details().get(0).orderQty(), "default (opt-in off) must never auto-apply the confirmed value");
    }

    @Test
    void secondSendCreatesRevision2AsCorrectionAndFreezesRevision1() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1); // Revision 1, orderedQty=3
        confirmWithQty(order.getId(), 2);
        orderRevisionService.createCorrection(order.getId(), new CreateRevisionRequest("correcting to 2", false), ADMIN);

        // ADMIN edits the Draft down to 2 via the ordinary, unchanged Draft-edit path.
        orderDraftService.updateDraft(order.getId(),
                new com.glv.gsysportal.dto.request.UpdateDraftRequest(null, null, null,
                        List.of(new com.glv.gsysportal.dto.request.UpdateDraftRequest.DetailQtyUpdate(
                                orderDraftService.getDraft(order.getId()).details().get(0).id(), 2))),
                ADMIN, true);
        PortalOrder reApproved = approveViaWorkflow(order.getId());
        PortalOrder resent = statusTransitionService.demoSend(reApproved.getId(), ADMIN);

        assertEquals(2, resent.getCurrentRevisionNo());
        List<OrderRevisionSummary> history = orderRevisionService.getRevisionHistory(order.getId());
        assertEquals(2, history.size());
        assertEquals("INITIAL", history.get(0).revisionType());
        assertEquals(3, history.get(0).lines().get(0).orderedQty(), "Revision 1 stays frozen at its original snapshotted value");
        assertEquals("CORRECTION", history.get(1).revisionType());
        assertEquals("correcting to 2", history.get(1).reason());
        assertEquals(2, history.get(1).lines().get(0).orderedQty());

        List<SupplierResponseHistoryEntry> responses = supplierResponseService.getResponseHistory(order.getId());
        assertEquals(2, responses.size());
        assertFalse(responses.get(0).isCurrent());
        assertTrue(responses.get(1).isCurrent());
    }

    // --- Difference Detection ---

    @Test
    void differencesReportQuantityChangedWhenConfirmedDiffersFromOrdered() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1); // orderedQty=3
        SupplierResponseView view = supplierResponseService.getSupplierResponse(order.getId());
        Long detailId = view.details().get(0).detailId();
        supplierResponseService.saveSupplierResponse(order.getId(), new SaveSupplierResponseRequest(null, null,
                List.of(new SaveSupplierResponseRequest.LineUpdate(detailId, 2, null, null, null))), ADMIN);

        SupplierResponseView afterSave = supplierResponseService.getSupplierResponse(order.getId());
        assertTrue(afterSave.differences().stream().anyMatch(d -> d.type().equals(ResponseDifferenceView.TYPE_QUANTITY_CHANGED)));
    }

    @Test
    void differencesReportUnansweredForNullConfirmedQty() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);

        SupplierResponseView view = supplierResponseService.getSupplierResponse(order.getId());
        assertTrue(view.differences().stream().anyMatch(d -> d.type().equals(ResponseDifferenceView.TYPE_UNANSWERED)));
    }

    // --- Supply Status (never auto-inferred) ---

    @Test
    void supplyStatusIsExplicitAndNeverAutoInferredFromZeroQty() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        Long detailId = supplierResponseService.getSupplierResponse(order.getId()).details().get(0).detailId();

        SupplierResponseView view = supplierResponseService.saveSupplierResponse(order.getId(), new SaveSupplierResponseRequest(null, null,
                List.of(new SaveSupplierResponseRequest.LineUpdate(detailId, 0, null, null, null))), ADMIN);

        assertNull(view.details().get(0).supplyStatus(), "confirmedQty=0 must NOT auto-set any Supply Status");
    }

    @Test
    void explicitNonAvailableSupplyStatusRaisesAttention() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        Long detailId = supplierResponseService.getSupplierResponse(order.getId()).details().get(0).detailId();

        SupplierResponseView view = supplierResponseService.saveSupplierResponse(order.getId(), new SaveSupplierResponseRequest(null, null,
                List.of(new SaveSupplierResponseRequest.LineUpdate(detailId, 3, null, null, "OUT_OF_STOCK"))), ADMIN);

        assertEquals("OUT_OF_STOCK", view.details().get(0).supplyStatus());
        assertTrue(view.details().get(0).attentions().stream().anyMatch(a -> a.attentionType().equals(OrderAttention.SUPPLY_STATUS_CHANGED)));
    }

    // --- Agreement / Reopen ---

    @Test
    void agreeTransitionsToAgreedAndWritesAudit() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        confirmWithQty(order.getId(), 3); // no difference -> no Attention

        SupplierResponseView view = supplierResponseService.getSupplierResponse(order.getId());
        PortalOrder agreed = supplierResponseService.agree(order.getId(), view.responseId(), new AgreeResponseRequest(false), ADMIN);

        assertEquals(PortalOrder.STATUS_AGREED, agreed.getStatus());
        SupplierResponseView afterAgree = supplierResponseService.getSupplierResponse(order.getId());
        assertEquals(ADMIN, afterAgree.agreedBy());
        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .anyMatch(e -> e.getEventType().equals(AuditEvent.SUPPLIER_RESPONSE_AGREED)));
    }

    @Test
    void confirmAloneNeverImpliesAgreed() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        PortalOrder confirmed = confirmWithQty(order.getId(), 3);

        assertEquals(PortalOrder.STATUS_SUPPLIER_CONFIRMED, confirmed.getStatus(),
                "7-C5 10章/19章: Supplier Response確定 ≠ AGREED must always hold");
    }

    @Test
    void agreeWithUnacknowledgedAttentionIsRejectedWithoutForce() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1); // orderedQty=3
        confirmWithQty(order.getId(), 2); // QUANTITY_CHANGED Attention, never acknowledged

        SupplierResponseView view = supplierResponseService.getSupplierResponse(order.getId());
        assertThrows(UnacknowledgedAttentionException.class, () -> supplierResponseService.agree(
                order.getId(), view.responseId(), new AgreeResponseRequest(false), ADMIN));
    }

    @Test
    void agreeSucceedsWithForceAgreeDespiteUnacknowledgedAttention() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        confirmWithQty(order.getId(), 2);

        SupplierResponseView view = supplierResponseService.getSupplierResponse(order.getId());
        PortalOrder agreed = supplierResponseService.agree(order.getId(), view.responseId(), new AgreeResponseRequest(true), ADMIN);

        assertEquals(PortalOrder.STATUS_AGREED, agreed.getStatus());
    }

    @Test
    void agreeSucceedsAfterAttentionAcknowledged() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        confirmWithQty(order.getId(), 2);

        List<OrderAttention> active = orderAttentionRepository.findByPortalOrderIdAndActiveTrue(order.getId());
        active.forEach(a -> attentionService.acknowledge(a.getId(), ADMIN));

        SupplierResponseView view = supplierResponseService.getSupplierResponse(order.getId());
        PortalOrder agreed = supplierResponseService.agree(order.getId(), view.responseId(), new AgreeResponseRequest(false), ADMIN);

        assertEquals(PortalOrder.STATUS_AGREED, agreed.getStatus());
    }

    @Test
    void agreeOnPastSupersededResponseIsRejected() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        confirmWithQty(order.getId(), 2);
        Long staleResponseId = supplierResponseService.getSupplierResponse(order.getId()).responseId();
        orderRevisionService.createCorrection(order.getId(), new CreateRevisionRequest("fixing", false), ADMIN);
        approveViaWorkflow(order.getId());
        statusTransitionService.demoSend(order.getId(), ADMIN); // now on Revision 2, staleResponseId belongs to Revision 1

        assertThrows(ResponseNotAgreeableException.class, () -> supplierResponseService.agree(
                order.getId(), staleResponseId, new AgreeResponseRequest(true), ADMIN));
    }

    @Test
    void reopenReturnsToSupplierConfirmedAndPreservesAgreedByHistory() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        confirmWithQty(order.getId(), 3);
        SupplierResponseView beforeAgree = supplierResponseService.getSupplierResponse(order.getId());
        supplierResponseService.agree(order.getId(), beforeAgree.responseId(), new AgreeResponseRequest(false), ADMIN);

        PortalOrder reopened = supplierResponseService.reopenAgreement(order.getId(), beforeAgree.responseId(),
                new ReopenAgreementRequest("found a mistake"), ADMIN);

        assertEquals(PortalOrder.STATUS_SUPPLIER_CONFIRMED, reopened.getStatus());
        SupplierResponseView afterReopen = supplierResponseService.getSupplierResponse(order.getId());
        assertEquals(ADMIN, afterReopen.agreedBy(), "agreed_by/agreed_at must never be cleared by Reopen (履歴を消さない)");
        assertEquals("found a mistake", afterReopen.reopenReason());
        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .anyMatch(e -> e.getEventType().equals(AuditEvent.AGREEMENT_REOPENED)));
    }

    @Test
    void reopenWithoutReasonIsRejected() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        confirmWithQty(order.getId(), 3);
        SupplierResponseView view = supplierResponseService.getSupplierResponse(order.getId());
        supplierResponseService.agree(order.getId(), view.responseId(), new AgreeResponseRequest(false), ADMIN);

        assertThrows(ReopenReasonRequiredException.class, () -> supplierResponseService.reopenAgreement(
                order.getId(), view.responseId(), new ReopenAgreementRequest(""), ADMIN));
    }

    @Test
    void reopenOutsideAgreedIsRejected() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        confirmWithQty(order.getId(), 3);
        SupplierResponseView view = supplierResponseService.getSupplierResponse(order.getId());

        assertThrows(OrderNotAgreedException.class, () -> supplierResponseService.reopenAgreement(
                order.getId(), view.responseId(), new ReopenAgreementRequest("reason"), ADMIN));
    }

    // --- Integration Request revision alignment (7-C5 16章) ---

    @Test
    void currentRevisionNoDoesNotAdvanceUntilTheNextActualSend() {
        // The pure numbering formula itself (7-C5 16章: Integration Request's
        // target revisionNo = currentRevisionNo==null?1:currentRevisionNo+1)
        // is exercised directly in OfficialPoIntegrationServiceIntegrationTest;
        // this test only asserts the precondition it depends on - that
        // "修正版を作成" alone never advances currentRevisionNo.
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        assertEquals(1, order.getCurrentRevisionNo());
        confirmWithQty(order.getId(), 2);

        PortalOrder corrected = orderRevisionService.createCorrection(order.getId(), new CreateRevisionRequest("fix", false), ADMIN);

        assertEquals(1, corrected.getCurrentRevisionNo());
        assertEquals(1, supplierResponseRepository.findByPortalOrderIdOrderByIdAsc(order.getId()).size(),
                "no new SupplierResponse row is created until the NEXT actual Send either");
    }
}
