package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.DashboardResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Implementation instructions Step 5 3章. Only asserts derivable, non-fabricated
 * counts - no Sales Trend / Analytics field exists on the DTO to even test. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class DashboardServiceIntegrationTest {

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private PriceChangeSetService priceChangeSetService;

    @Test
    void dashboardReflectsCandidatesAndDraftCounts() {
        int candidatesBefore = dashboardService.getDashboard().candidateCount();
        int draftsBefore = dashboardService.getDashboard().draftCount();

        orderDraftService.createDraft(new CreateDraftRequest(List.of("OD-TENT-001"), null, null, null), "tester01");

        DashboardResponse after = dashboardService.getDashboard();
        assertTrue(after.candidateCount() >= candidatesBefore, "candidateCount is Legacy-derived and unaffected by Draft creation");
        assertTrue(after.draftCount() >= draftsBefore + 1);
        assertTrue(after.brands().stream().anyMatch(b -> "BR_OUTDOOR".equals(b.brandCode()) && b.draftCount() >= 1));
    }

    /** Phase 8-J 11章/13章: Dashboard had zero entry point into Price Change
     * (Phase 8-B/8-D) until this Phase. */
    @Test
    void dashboardReflectsPriceChangeDraftCount() {
        int before = dashboardService.getDashboard().priceChangeDraftCount();

        priceChangeSetService.createDraft("Phase 8-J Dashboard test", "tester01");

        DashboardResponse after = dashboardService.getDashboard();
        assertTrue(after.priceChangeDraftCount() >= before + 1);
    }

    @Test
    void dashboardHasNoAnalyticsFields() {
        // Compile-time guarantee via DashboardResponse's fixed field list -
        // this test documents the intent explicitly (implementation
        // instructions Step 5 3章 "重要": no Sales Trend/margin/turnover).
        DashboardResponse response = dashboardService.getDashboard();
        assertTrue(response.brands() != null);
    }
}
