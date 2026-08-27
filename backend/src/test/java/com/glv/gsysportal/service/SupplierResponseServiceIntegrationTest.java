package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.dto.response.SupplierResponseDetailView;
import com.glv.gsysportal.dto.response.SupplierResponseView;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.InvalidConfirmedQtyException;
import com.glv.gsysportal.exception.InvalidOrderStatusException;
import com.glv.gsysportal.exception.InvalidStatusTransitionException;
import com.glv.gsysportal.exception.SupplierResponseIncompleteException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Implementation instructions 29章 Supplier Response test list. Same
 * whole-test-method Prototype transaction + rollback pattern as the other
 * Step 2/3 integration tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class SupplierResponseServiceIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // recommendedQty=3
    private static final String SKU_TENT_2 = "OD-TENT-002"; // recommendedQty=20

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private SupplierResponseService supplierResponseService;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private PortalOrder createAwaitingSupplierOrder(String... skus) {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(skus), null, null, null), "tester01");
        PortalOrder ready = statusTransitionService.confirm(draft.id(), "tester01");
        return statusTransitionService.demoSend(ready.getId(), "tester01");
    }

    private static SaveSupplierResponseRequest.LineUpdate line(Long detailId, Integer confirmedQty, LocalDate delivery) {
        return new SaveSupplierResponseRequest.LineUpdate(detailId, confirmedQty, delivery, null);
    }

    @Test
    void responseIsInitializedWithBothLinesUnanswered() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1, SKU_TENT_2);

        SupplierResponseView view = supplierResponseService.getSupplierResponse(order.getId());

        assertEquals(2, view.details().size());
        assertTrue(view.details().stream().allMatch(d -> d.confirmedQty() == null));
        assertEquals(0, view.summary().answeredCount());
        assertEquals(2, view.summary().unansweredCount());
        assertEquals("PARTIAL", view.responseStatus());
    }

    @Test
    void getOnDraftStatusOrderIsRejected() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        assertThrows(InvalidOrderStatusException.class, () -> supplierResponseService.getSupplierResponse(draft.id()));
    }

    @Test
    void partialSaveLeavesOtherLineUntouched() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1, SKU_TENT_2);
        Long detail1 = detailId(order, 0);

        SupplierResponseView view = supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detail1, 3, null))), "tester01");

        SupplierResponseDetailView d1 = findByDetailId(view, detail1);
        assertEquals(3, d1.confirmedQty());
        SupplierResponseDetailView d2 = view.details().stream().filter(d -> !d.detailId().equals(detail1)).findFirst().orElseThrow();
        assertNull(d2.confirmedQty(), "the other line must remain untouched by a partial save");
    }

    @Test
    void confirmedQtyZeroIsDistinctFromNull() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        Long detailId = detailId(order, 0);

        SupplierResponseView beforeSave = supplierResponseService.getSupplierResponse(order.getId());
        assertNull(beforeSave.details().get(0).confirmedQty());

        SupplierResponseView afterSave = supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 0, null))), "tester01");

        assertEquals(0, afterSave.details().get(0).confirmedQty(), "0 must be stored as an explicit answer, not coerced to null");
        assertTrue(afterSave.details().get(0).isConfirmed());
        assertEquals(1, afterSave.summary().answeredCount());
        assertEquals(1, afterSave.summary().zeroQtyCount());
    }

    @Test
    void confirmedQtyLessThanOrderedTriggersQuantityChangedNoWarning() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1); // orderedQty = 3
        Long detailId = detailId(order, 0);

        SupplierResponseView view = supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 2, null))), "tester01");

        assertTrue(view.details().get(0).attentionTypes().contains(OrderAttention.QUANTITY_CHANGED));
        assertTrue(view.details().get(0).warningCodes().isEmpty(), "less-than is not a Warning case");
    }

    @Test
    void confirmedQtyEqualsOrderedDoesNotTriggerAttention() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1); // orderedQty = 3
        Long detailId = detailId(order, 0);

        SupplierResponseView view = supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 3, null))), "tester01");

        assertTrue(view.details().get(0).attentionTypes().isEmpty());
        assertTrue(view.details().get(0).warningCodes().isEmpty());
    }

    @Test
    void confirmedQtyGreaterThanOrderedTriggersAttentionAndWarning() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1); // orderedQty = 3

        SupplierResponseView view = supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId(order, 0), 5, null))), "tester01");

        assertTrue(view.details().get(0).attentionTypes().contains(OrderAttention.QUANTITY_CHANGED));
        assertTrue(view.details().get(0).warningCodes().contains("CONFIRMED_QTY_EXCEEDS_ORDERED_QTY"),
                "greater-than is allowed but must carry a Warning code, never an Error");
    }

    @Test
    void negativeConfirmedQtyIsRejected() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);

        assertThrows(InvalidConfirmedQtyException.class, () -> supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId(order, 0), -1, null))), "tester01"));
    }

    @Test
    void confirmedDeliveryDifferentFromRequestedTriggersAttention() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, LocalDate.of(2026, 9, 15), null), "tester01");
        PortalOrder ready = statusTransitionService.confirm(draft.id(), "tester01");
        PortalOrder order = statusTransitionService.demoSend(ready.getId(), "tester01");
        Long detailId = detailId(order, 0);

        SupplierResponseView view = supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 3, LocalDate.of(2026, 9, 20)))), "tester01");

        assertTrue(view.details().get(0).attentionTypes().contains(OrderAttention.DELIVERY_CHANGED));
    }

    @Test
    void nullConfirmedDeliveryIsNotTreatedAsDifferentFromRequested() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, LocalDate.of(2026, 9, 15), null), "tester01");
        PortalOrder ready = statusTransitionService.confirm(draft.id(), "tester01");
        PortalOrder order = statusTransitionService.demoSend(ready.getId(), "tester01");
        Long detailId = detailId(order, 0);

        // Answer qty only, leave confirmedDelivery null.
        SupplierResponseView view = supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 3, null))), "tester01");

        assertFalse(view.details().get(0).attentionTypes().contains(OrderAttention.DELIVERY_CHANGED),
                "implementation instructions 13章: null confirmedDelivery must not be treated as a difference");
    }

    @Test
    void partialAnswerSetsOrderLevelPartialConfirmationAttention() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1, SKU_TENT_2);

        SupplierResponseView view = supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId(order, 0), 3, null))), "tester01");

        assertTrue(view.orderAttentionTypes().contains(OrderAttention.PARTIAL_CONFIRMATION));
    }

    @Test
    void fullyAnsweredAutoResolvesPartialConfirmation() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1, SKU_TENT_2);
        Long d1 = detailId(order, 0);
        Long d2 = detailId(order, 1);

        supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(d1, 3, null))), "tester01");
        SupplierResponseView finalView = supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(d2, 20, null))), "tester01");

        assertFalse(finalView.orderAttentionTypes().contains(OrderAttention.PARTIAL_CONFIRMATION),
                "PARTIAL_CONFIRMATION auto-resolves once every line has an answer (implementation instructions 23章)");
    }

    @Test
    void repeatedSaveDoesNotDuplicateActiveAttention() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1); // orderedQty=3
        Long detailId = detailId(order, 0);

        supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 1, null))), "tester01");
        SupplierResponseView view = supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 2, null))), "tester01");

        long qtyChangedCount = view.details().get(0).attentionTypes().stream()
                .filter(t -> t.equals(OrderAttention.QUANTITY_CHANGED)).count();
        assertEquals(1, qtyChangedCount, "no duplicate ACTIVE Attention despite two separate qty changes on the same line");
    }

    @Test
    void multipleUpdatesPreserveFullAuditHistory() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        Long detailId = detailId(order, 0);

        supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 9, null))), "tester01");
        supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 8, null))), "tester01");

        List<AuditEvent> qtyEvents = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .filter(e -> e.getEventType().equals(AuditEvent.QUANTITY_CHANGED))
                .toList();
        assertEquals(2, qtyEvents.size());
        assertNull(qtyEvents.get(0).getOldValue());
        assertEquals("9", qtyEvents.get(0).getNewValue());
        assertEquals("9", qtyEvents.get(1).getOldValue());
        assertEquals("8", qtyEvents.get(1).getNewValue());
    }

    @Test
    void unchangedSaveWritesNoNewAudit() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        Long detailId = detailId(order, 0);

        supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 3, null))), "tester01");
        int countAfterFirstSave = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).size();

        supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId, 3, null))), "tester01");

        assertEquals(countAfterFirstSave, auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).size(),
                "re-saving the same value must not add a new QUANTITY_CHANGED audit row");
    }

    @Test
    void saveWhileNotAwaitingSupplierIsRejected() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");
        PortalOrder ready = statusTransitionService.confirm(draft.id(), "tester01"); // READY_TO_ORDER, not sent yet

        assertThrows(InvalidStatusTransitionException.class, () -> supplierResponseService.saveSupplierResponse(ready.getId(),
                new SaveSupplierResponseRequest(null, null, List.of()), "tester01"));
    }

    @Test
    void confirmRejectsWhenIncomplete() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1, SKU_TENT_2);
        supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId(order, 0), 3, null))), "tester01");
        // second line still unanswered

        assertThrows(SupplierResponseIncompleteException.class,
                () -> supplierResponseService.confirmSupplierResponse(order.getId(), "tester01"));
    }

    @Test
    void confirmSucceedsWhenComplete() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1, SKU_TENT_2);
        supplierResponseService.saveSupplierResponse(order.getId(), new SaveSupplierResponseRequest(null, null, List.of(
                line(detailId(order, 0), 3, null), line(detailId(order, 1), 0, null)
        )), "tester01");

        PortalOrder confirmed = supplierResponseService.confirmSupplierResponse(order.getId(), "tester01");

        assertEquals(PortalOrder.STATUS_SUPPLIER_CONFIRMED, confirmed.getStatus());
    }

    @Test
    void confirmWritesSupplierResponseReceivedAndStatusChangedAudit() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId(order, 0), 3, null))), "tester01");

        supplierResponseService.confirmSupplierResponse(order.getId(), "tester01");

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals(AuditEvent.SUPPLIER_RESPONSE_RECEIVED)));
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals(AuditEvent.STATUS_CHANGED)
                && "AWAITING_SUPPLIER".equals(e.getOldValue()) && "SUPPLIER_CONFIRMED".equals(e.getNewValue())));
    }

    @Test
    void doubleConfirmIsRejected() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId(order, 0), 3, null))), "tester01");
        supplierResponseService.confirmSupplierResponse(order.getId(), "tester01");
        int eventsAfterFirstConfirm = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).size();

        assertThrows(InvalidStatusTransitionException.class,
                () -> supplierResponseService.confirmSupplierResponse(order.getId(), "tester02"));

        assertEquals(eventsAfterFirstConfirm, auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).size());
    }

    @Test
    void saveIsRejectedAfterConfirm() {
        PortalOrder order = createAwaitingSupplierOrder(SKU_TENT_1);
        supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId(order, 0), 3, null))), "tester01");
        supplierResponseService.confirmSupplierResponse(order.getId(), "tester01");

        assertThrows(InvalidStatusTransitionException.class, () -> supplierResponseService.saveSupplierResponse(order.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(line(detailId(order, 0), 2, null))), "tester01"));
    }

    @Test
    void getOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> supplierResponseService.getSupplierResponse(-1L));
    }

    /** {@code SupplierResponseDetail.id} (the API's {@code detailId}) is a
     * distinct id space from {@code PortalOrderDetail.id} - always resolve
     * it through the Service's own view rather than assuming any numeric
     * relationship between the two. Ordering matches Demo Send's
     * initialization order (line_no ASC), which is also
     * {@code SupplierResponse.details}'s {@code @OrderBy("id ASC")}. */
    private Long detailId(PortalOrder order, int lineIndex) {
        return supplierResponseService.getSupplierResponse(order.getId()).details().get(lineIndex).detailId();
    }

    private static SupplierResponseDetailView findByDetailId(SupplierResponseView view, Long detailId) {
        return view.details().stream().filter(d -> d.detailId().equals(detailId)).findFirst().orElseThrow();
    }
}
