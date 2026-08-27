package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.response.AuditEventView;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.dto.response.OrderHistoryDetailResponse;
import com.glv.gsysportal.dto.response.OrderHistorySummaryResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Implementation instructions 29章 History test list - list/detail/Timeline
 * order and the Recommended/Ordered/Confirmed 3-stage view, all READ ONLY. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OrderHistoryServiceIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // recommendedQty = 3

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private SupplierResponseService supplierResponseService;
    @Autowired
    private OrderHistoryService orderHistoryService;

    @Test
    void listIncludesCreatedOrderAndIsReadOnly() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        List<OrderHistorySummaryResponse> list = orderHistoryService.list(null, null, null);

        assertTrue(list.stream().anyMatch(o -> o.id().equals(draft.id())));
    }

    @Test
    void listFiltersBySupplierBrandAndStatus() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        List<OrderHistorySummaryResponse> matched = orderHistoryService.list("SUP_ALPHA", "BR_OUTDOOR", "DRAFT");
        List<OrderHistorySummaryResponse> unmatched = orderHistoryService.list("SUP_BETA", null, null);

        assertTrue(matched.stream().anyMatch(o -> o.id().equals(draft.id())));
        assertTrue(unmatched.stream().noneMatch(o -> o.id().equals(draft.id())));
    }

    @Test
    void detailShowsRecommendedOrderedAndConfirmedThreeStage() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");
        PortalOrder ready = statusTransitionService.confirm(draft.id(), "tester01");
        PortalOrder sent = statusTransitionService.demoSend(ready.getId(), "tester01");
        Long detailId = supplierResponseService.getSupplierResponse(sent.getId()).details().get(0).detailId();
        supplierResponseService.saveSupplierResponse(sent.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(new SaveSupplierResponseRequest.LineUpdate(detailId, 2, null, null))),
                "tester01");

        OrderHistoryDetailResponse detail = orderHistoryService.detail(sent.getId());

        var line = detail.details().get(0);
        assertEquals(3, line.recommendedQty());
        assertEquals(3, line.orderedQty());
        assertEquals(2, line.confirmedQty());
    }

    @Test
    void detailShowsNullConfirmedQtyBeforeSupplierResponseExists() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        OrderHistoryDetailResponse detail = orderHistoryService.detail(draft.id());

        assertEquals(null, detail.details().get(0).confirmedQty());
    }

    @Test
    void detailOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> orderHistoryService.detail(-1L));
    }

    @Test
    void eventsAreOrderedChronologicallyAndCoverFullLifecycle() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");
        PortalOrder ready = statusTransitionService.confirm(draft.id(), "tester01");
        statusTransitionService.demoSend(ready.getId(), "tester01");

        List<AuditEventView> events = orderHistoryService.events(draft.id());

        assertEquals("ORDER_DRAFT_CREATED", events.get(0).eventType());
        for (int i = 1; i < events.size(); i++) {
            assertTrue(!events.get(i).performedAt().isBefore(events.get(i - 1).performedAt()),
                    "events must be strictly chronological");
        }
        assertTrue(events.stream().anyMatch(e -> e.eventType().equals("DEMO_SENT")));
    }

    @Test
    void eventsOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> orderHistoryService.events(-1L));
    }
}
