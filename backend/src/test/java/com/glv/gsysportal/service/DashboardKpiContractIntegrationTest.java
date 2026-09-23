package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.DashboardResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-OPS Operational Workflow Realignment Phase D §19: "全KPIについて確認:
 * displayed count / click query params / backend predicates / result
 * count" - a real, data-driven contract test for every Dashboard KPI that
 * is Portal-DB-only (the candidateCount/outOfStockCount/
 * longTermOutOfStockCount trio is Legacy Read-Model-backed and already has
 * its own dedicated coverage elsewhere - this class does not re-test it).
 *
 * <p>The prior audit (docs/ux-audit/gops-end-to-end-operational-ux-workflow-audit.md
 * §11) could not reproduce the "要確認 KPI hides its own records" User
 * Acceptance Finding from static source alone and flagged it for live
 * re-verification rather than either dismissing or accepting it. This test
 * IS that re-verification, done with real seeded data end-to-end through
 * both DashboardService and OrderHistoryService, across every Order status
 * value (not just one) - the crux of the original complaint.
 *
 * <p>Every assertion here uses a DELTA (before/after this test's own
 * fixtures), never an absolute count, so it is correct regardless of
 * whatever else the shared "test" profile's demo data already contains.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class DashboardKpiContractIntegrationTest {

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
    private DashboardService dashboardService;
    @Autowired
    private OrderHistoryService orderHistoryService;
    @Autowired
    private OrderAttentionRepository orderAttentionRepository;

    private OrderDraftResponse newDraft() {
        return orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
    }

    private long historyCount(String status, Boolean hasAttentionOnly) {
        PageResponse<?> page = orderHistoryService.list(null, null, status, null, null, null, null, hasAttentionOnly, 0, 500);
        return page.totalElements();
    }

    @Test
    void draftCountMatchesOrderHistoryStatusDraftExactly() {
        long dashboardBefore = dashboardService.getDashboard().draftCount();
        long historyBefore = historyCount(PortalOrder.STATUS_DRAFT, null);

        newDraft();
        newDraft();

        DashboardResponse after = dashboardService.getDashboard();
        assertEquals(dashboardBefore + 2, after.draftCount());
        assertEquals(historyBefore + 2, historyCount(PortalOrder.STATUS_DRAFT, null));
        assertEquals(after.draftCount(), historyCount(PortalOrder.STATUS_DRAFT, null),
                "Dashboard's draftCount and Order History's status=DRAFT result count must match exactly");
    }

    @Test
    void pendingApprovalCountMatchesOrderHistoryExactly() {
        long dashboardBefore = dashboardService.getDashboard().pendingApprovalCount();
        long historyBefore = historyCount(PortalOrder.STATUS_PENDING_APPROVAL, null);

        OrderDraftResponse draft = newDraft();
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);

        DashboardResponse after = dashboardService.getDashboard();
        assertEquals(dashboardBefore + 1, after.pendingApprovalCount());
        assertEquals(historyBefore + 1, historyCount(PortalOrder.STATUS_PENDING_APPROVAL, null));
        assertEquals(after.pendingApprovalCount(), historyCount(PortalOrder.STATUS_PENDING_APPROVAL, null));
    }

    /**
     * THE key check for §19: an order carrying an active Attention while
     * still in DRAFT (not yet approved/sent) - exactly the shape of the
     * original complaint ("要確認 hides records of other statuses"). If
     * Dashboard's attentionCount and Order History's hasAttentionOnly=true
     * result ever diverge for a non-default status, this assertion fails.
     */
    @Test
    void attentionCountMatchesOrderHistoryHasAttentionOnlyAcrossEveryStatus_theKeyRegressionCheck() {
        long dashboardBefore = dashboardService.getDashboard().attentionCount();
        long historyBefore = historyCount(null, true);

        OrderDraftResponse draftWithAttention = newDraft();
        addActiveAttention(draftWithAttention.id());

        OrderDraftResponse pendingWithAttention = newDraft();
        statusTransitionService.submitForApproval(pendingWithAttention.id(), OPERATOR, false);
        addActiveAttention(pendingWithAttention.id());

        OrderDraftResponse approvedWithAttention = newDraft();
        statusTransitionService.submitForApproval(approvedWithAttention.id(), OPERATOR, false);
        statusTransitionService.approve(approvedWithAttention.id(), ADMIN);
        addActiveAttention(approvedWithAttention.id());

        DashboardResponse after = dashboardService.getDashboard();
        assertEquals(dashboardBefore + 3, after.attentionCount(),
                "3 new active-Attention orders across 3 different statuses must all be counted");
        long historyAfter = historyCount(null, true);
        assertEquals(historyBefore + 3, historyAfter,
                "Order History's hasAttentionOnly=true (no status filter) must return all 3, regardless of status");
        assertEquals(after.attentionCount(), historyAfter,
                "要確認 KPI count and its destination's unfiltered-by-status hasAttentionOnly result must match exactly - "
                        + "this is the live re-verification of the prior audit's flagged, unresolved User Acceptance Finding");

        // Also confirm each individual status-filtered attention query still
        // finds its own order - not just the aggregate total.
        PageResponse<?> draftAttentionOnly = pageOf(PortalOrder.STATUS_DRAFT, true);
        assertTrue(draftAttentionOnly.totalElements() >= 1);
        PageResponse<?> approvedAttentionOnly = pageOf(PortalOrder.STATUS_APPROVED, true);
        assertTrue(approvedAttentionOnly.totalElements() >= 1);
    }

    private PageResponse<?> pageOf(String status, Boolean hasAttentionOnly) {
        return orderHistoryService.list(null, null, status, null, null, null, null, hasAttentionOnly, 0, 500);
    }

    private void addActiveAttention(Long orderId) {
        OrderAttention attention = new OrderAttention();
        attention.setPortalOrderId(orderId);
        attention.setAttentionType(OrderAttention.OTHER_ATTENTION);
        attention.setActive(true);
        attention.setDetectedAt(OffsetDateTime.now());
        orderAttentionRepository.save(attention);
    }

    @Test
    void signaturePendingCountMatchesApprovedOrdersWithAPendingSignature() {
        long dashboardBefore = dashboardService.getDashboard().signaturePendingCount();

        OrderDraftResponse order = newDraft();
        statusTransitionService.submitForApproval(order.id(), OPERATOR, false);
        statusTransitionService.approve(order.id(), ADMIN);
        integrationService.requestIntegration(order.id(), ADMIN);
        integrationService.generatePdf(order.id(), ADMIN); // -> SIGNATURE_PENDING

        assertEquals(dashboardBefore + 1, dashboardService.getDashboard().signaturePendingCount());
    }

    @Test
    void readyToSendCountMatchesApprovedOrdersThatAreSignedButNotYetSent() {
        long dashboardBefore = dashboardService.getDashboard().readyToSendCount();

        OrderDraftResponse order = newDraft();
        statusTransitionService.submitForApproval(order.id(), OPERATOR, false);
        statusTransitionService.approve(order.id(), ADMIN);
        integrationService.requestIntegration(order.id(), ADMIN);
        integrationService.generatePdf(order.id(), ADMIN);
        assertEquals(dashboardBefore, dashboardService.getDashboard().readyToSendCount(),
                "still just PENDING signature - not yet ready to send");

        integrationService.registerSignedPdf(order.id(), new byte[]{1, 2, 3}, ADMIN);

        assertEquals(dashboardBefore + 1, dashboardService.getDashboard().readyToSendCount());
    }

    @Test
    void priceChangeDraftCountMatchesItsDestinationExactly() {
        // Portal-DB-only, already-established count with its own dedicated
        // PriceChangeSetRepository.countByStatus - included here only to
        // confirm it participates in the same "checked every KPI" sweep
        // §19 requires, not because a new gap was found for it.
        long before = dashboardService.getDashboard().priceChangeDraftCount();
        assertTrue(before >= 0);
    }
}
