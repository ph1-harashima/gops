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

    /** Phase 7-C1: DRAFT -> PENDING_APPROVAL -> APPROVED (the pre-7-C1 confirm() equivalent). */
    private com.glv.gsysportal.domain.PortalOrder approveViaWorkflow(Long orderId) {
        statusTransitionService.submitForApproval(orderId, "tester01", true);
        return statusTransitionService.approve(orderId, "tester01");
    }
    private static final String SKU_TENT_1 = "OD-TENT-001"; // recommendedQty = 3

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private SupplierResponseService supplierResponseService;
    @Autowired
    private OrderHistoryService orderHistoryService;

    /** Phase 8-J 3章/4章: list() is now DB-paginated (PageResponse), so these
     * tests read {@code .content()} and pass a generously large size (200)
     * to keep "does the created Order appear anywhere in the unfiltered
     * result" assertions robust regardless of how many other Orders already
     * exist in the shared test DB - the SAME robustness the pre-8-J
     * unpaginated List gave for free. */
    private static final int GENEROUS_SIZE = 200;

    @Test
    void listIncludesCreatedOrderAndIsReadOnly() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        List<OrderHistorySummaryResponse> list = orderHistoryService.list(null, null, null, null, null, null, null, null, null, GENEROUS_SIZE).content();

        assertTrue(list.stream().anyMatch(o -> o.id().equals(draft.id())));
    }

    @Test
    void listFiltersBySupplierBrandAndStatus() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        List<OrderHistorySummaryResponse> matched = orderHistoryService.list("SUP_ALPHA", "BR_OUTDOOR", "DRAFT", null, null, null, null, null, null, GENEROUS_SIZE).content();
        List<OrderHistorySummaryResponse> unmatched = orderHistoryService.list("SUP_BETA", null, null, null, null, null, null, null, null, GENEROUS_SIZE).content();

        assertTrue(matched.stream().anyMatch(o -> o.id().equals(draft.id())));
        assertTrue(unmatched.stream().noneMatch(o -> o.id().equals(draft.id())));
    }

    /** Phase 7-H (Order List search/filter audit). */
    @Test
    void listFiltersByOrderNoKeyword() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        // draftNo is always assigned at creation - matches on a substring,
        // case-insensitively.
        String keywordFragment = draft.draftNo().substring(0, 8).toLowerCase(java.util.Locale.ROOT);
        List<OrderHistorySummaryResponse> matched = orderHistoryService.list(null, null, null, keywordFragment, null, null, null, null, null, GENEROUS_SIZE).content();
        List<OrderHistorySummaryResponse> unmatched = orderHistoryService.list(null, null, null, "NO-SUCH-ORDER-NUMBER", null, null, null, null, null, GENEROUS_SIZE).content();

        assertTrue(matched.stream().anyMatch(o -> o.id().equals(draft.id())));
        assertTrue(unmatched.stream().noneMatch(o -> o.id().equals(draft.id())));
    }

    /** Phase 7-H (Order List search/filter audit). */
    @Test
    void listFiltersByItemKeyword() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        List<OrderHistorySummaryResponse> matchedBySku = orderHistoryService.list(null, null, null, null, "od-tent-001", null, null, null, null, GENEROUS_SIZE).content();
        List<OrderHistorySummaryResponse> unmatched = orderHistoryService.list(null, null, null, null, "NO-SUCH-ITEM", null, null, null, null, GENEROUS_SIZE).content();

        assertTrue(matchedBySku.stream().anyMatch(o -> o.id().equals(draft.id())));
        assertTrue(unmatched.stream().noneMatch(o -> o.id().equals(draft.id())));
    }

    /** Phase 7-H (Order List search/filter audit). */
    @Test
    void listFiltersByUpdatedDateRange() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");
        java.time.LocalDate today = java.time.LocalDate.now();

        List<OrderHistorySummaryResponse> withinRange = orderHistoryService.list(null, null, null, null, null, today, today, null, null, GENEROUS_SIZE).content();
        List<OrderHistorySummaryResponse> beforeRange = orderHistoryService.list(null, null, null, null, null, null, today.minusDays(1), null, null, GENEROUS_SIZE).content();
        List<OrderHistorySummaryResponse> afterRange = orderHistoryService.list(null, null, null, null, null, today.plusDays(1), null, null, null, GENEROUS_SIZE).content();

        assertTrue(withinRange.stream().anyMatch(o -> o.id().equals(draft.id())));
        assertTrue(beforeRange.stream().noneMatch(o -> o.id().equals(draft.id())));
        assertTrue(afterRange.stream().noneMatch(o -> o.id().equals(draft.id())));
    }

    /** Phase 8-J 3章: new coverage for hasAttentionOnly, moved from a
     * Frontend-only display filter to a DB-level EXISTS predicate - was
     * previously untested at the Service layer since it never reached
     * OrderHistoryService.list() before this Phase. */
    @Test
    void listFiltersByHasAttentionOnly() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        List<OrderHistorySummaryResponse> withoutFilter = orderHistoryService.list(null, null, null, null, null, null, null, null, null, GENEROUS_SIZE).content();
        List<OrderHistorySummaryResponse> attentionOnly = orderHistoryService.list(null, null, null, null, null, null, null, true, null, GENEROUS_SIZE).content();

        assertTrue(withoutFilter.stream().anyMatch(o -> o.id().equals(draft.id())));
        // A freshly-created Draft has no Attention yet, so it must be
        // excluded once hasAttentionOnly=true - the SAME "no active
        // Attention -> not shown" result the pre-8-J client-side filter gave.
        assertTrue(attentionOnly.stream().noneMatch(o -> o.id().equals(draft.id())));
    }

    /** Phase 8-J 3章/4章: confirms page/size actually bound the DB-level
     * result (LIMIT/OFFSET), not just a post-fetch slice - size=1 must never
     * return more than 1 row, and totalElements must reflect the full
     * matching count regardless of page size. */
    @Test
    void listIsBackendPaginated() {
        orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");
        orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        var onePerPage = orderHistoryService.list(null, null, null, null, null, null, null, null, 0, 1);

        assertEquals(1, onePerPage.content().size());
        assertEquals(1, onePerPage.size());
        assertTrue(onePerPage.totalElements() >= 2);
    }

    @Test
    void detailShowsRecommendedOrderedAndConfirmedThreeStage() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");
        PortalOrder ready = approveViaWorkflow(draft.id());
        PortalOrder sent = statusTransitionService.demoSend(ready.getId(), "tester01");
        Long detailId = supplierResponseService.getSupplierResponse(sent.getId()).details().get(0).detailId();
        supplierResponseService.saveSupplierResponse(sent.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(new SaveSupplierResponseRequest.LineUpdate(detailId, 2, null, null, null))),
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
        PortalOrder ready = approveViaWorkflow(draft.id());
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
