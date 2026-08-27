package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.InvalidStatusTransitionException;
import com.glv.gsysportal.exception.NoOrderableItemsException;
import com.glv.gsysportal.exception.OrderNotEditableException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Implementation instructions 19章 Step 3 test list: Confirm Order
 * transition/idempotency/PO No. reuse, Return to Draft transition, and the
 * Draft edit lock while READY_TO_ORDER. Same pattern as
 * {@link OrderDraftServiceIntegrationTest} - whole-test-method Prototype
 * transaction, always rolled back, so no data is left behind. The Legacy
 * read side (Create Draft's own re-fetch) is a separate transaction/
 * DataSource and unaffected by this rollback.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OrderStatusTransitionServiceIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // SUP_ALPHA, calc4 recommendedQty = 3
    private static final String SKU_CHAIR_0 = "OD-CHAIR-001"; // SUP_ALPHA, calc4 recommendedQty = 0

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private OrderDraftResponse createDraft(String... skus) {
        return orderDraftService.createDraft(new CreateDraftRequest(List.of(skus), null, null, null), "tester01");
    }

    @Test
    void confirmTransitionsDraftToReadyToOrderAndAssignsPoNo() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        PortalOrder confirmed = statusTransitionService.confirm(draft.id(), "tester01");

        assertEquals(PortalOrder.STATUS_READY_TO_ORDER, confirmed.getStatus());
        assertNotNull(confirmed.getPrototypePoNo());
        assertTrue(confirmed.getPrototypePoNo().startsWith("PO-DEMO-"));
        assertEquals("tester01", confirmed.getUpdatedBy());
    }

    @Test
    void confirmWritesOrderReadyAndStatusChangedAudit() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        statusTransitionService.confirm(draft.id(), "tester01");

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(draft.id());
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals(AuditEvent.ORDER_READY)));
        assertTrue(events.stream().anyMatch(e ->
                e.getEventType().equals(AuditEvent.STATUS_CHANGED)
                        && "DRAFT".equals(e.getOldValue()) && "READY_TO_ORDER".equals(e.getNewValue())
        ));
    }

    @Test
    void confirmOnAlreadyReadyToOrderOrderIsRejectedIdempotently() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        PortalOrder firstConfirm = statusTransitionService.confirm(draft.id(), "tester01");
        String firstPoNo = firstConfirm.getPrototypePoNo();
        int eventsAfterFirstConfirm = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(draft.id()).size();

        assertThrows(InvalidStatusTransitionException.class, () -> statusTransitionService.confirm(draft.id(), "tester02"));

        List<AuditEvent> eventsAfterSecondAttempt = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(draft.id());
        assertEquals(eventsAfterFirstConfirm, eventsAfterSecondAttempt.size(), "rejected re-Confirm must not write any Audit rows");
        assertEquals(firstPoNo, firstConfirm.getPrototypePoNo(), "PO No. must be unchanged by the rejected re-Confirm");
    }

    @Test
    void confirmWithNoOrderableLinesIsRejected() {
        OrderDraftResponse draft = createDraft(SKU_CHAIR_0); // recommendedQty=0 -> initial orderQty=0

        assertThrows(NoOrderableItemsException.class, () -> statusTransitionService.confirm(draft.id(), "tester01"));
    }

    @Test
    void confirmOnUnknownDraftThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> statusTransitionService.confirm(-1L, "tester01"));
    }

    @Test
    void returnToDraftTransitionsBackAndPreservesPoNo() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        PortalOrder confirmed = statusTransitionService.confirm(draft.id(), "tester01");
        String poNo = confirmed.getPrototypePoNo();

        PortalOrder returned = statusTransitionService.returnToDraft(draft.id(), "tester02");

        assertEquals(PortalOrder.STATUS_DRAFT, returned.getStatus());
        assertEquals(poNo, returned.getPrototypePoNo(), "prototype_po_no must never be cleared or changed by Return to Draft");
    }

    @Test
    void reConfirmAfterReturnToDraftReusesTheSamePoNo() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        PortalOrder firstConfirm = statusTransitionService.confirm(draft.id(), "tester01");
        String firstPoNo = firstConfirm.getPrototypePoNo();

        statusTransitionService.returnToDraft(draft.id(), "tester01");
        PortalOrder secondConfirm = statusTransitionService.confirm(draft.id(), "tester01");

        assertEquals(firstPoNo, secondConfirm.getPrototypePoNo(),
                "implementation instructions 14章: re-Confirm must reuse the existing PO No., never generate a new one");
    }

    @Test
    void returnToDraftWritesStatusChangedAndReturnedToDraftAudit() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.confirm(draft.id(), "tester01");

        statusTransitionService.returnToDraft(draft.id(), "tester02");

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(draft.id());
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals(AuditEvent.ORDER_RETURNED_TO_DRAFT)));
        assertTrue(events.stream().anyMatch(e ->
                e.getEventType().equals(AuditEvent.STATUS_CHANGED)
                        && "READY_TO_ORDER".equals(e.getOldValue()) && "DRAFT".equals(e.getNewValue())
        ));
    }

    @Test
    void returnToDraftOnAlreadyDraftOrderIsRejected() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1); // still DRAFT

        assertThrows(InvalidStatusTransitionException.class, () -> statusTransitionService.returnToDraft(draft.id(), "tester01"));
    }

    @Test
    void draftIsNotEditableWhileReadyToOrder() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.confirm(draft.id(), "tester01");

        assertThrows(OrderNotEditableException.class, () -> orderDraftService.updateDraft(
                draft.id(),
                new com.glv.gsysportal.dto.request.UpdateDraftRequest(null, null, "should be rejected", null),
                "tester02"
        ));
    }

    @Test
    void draftIsEditableAgainAfterReturnToDraft() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        statusTransitionService.confirm(draft.id(), "tester01");
        statusTransitionService.returnToDraft(draft.id(), "tester01");

        OrderDraftResponse updated = orderDraftService.updateDraft(
                draft.id(),
                new com.glv.gsysportal.dto.request.UpdateDraftRequest(null, null, "editable again", null),
                "tester02"
        );

        assertEquals("editable again", updated.remark());
    }
}
