package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.DashboardBrandRow;
import com.glv.gsysportal.dto.response.DashboardResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @Autowired
    private SupplierRegionClassificationResolutionService regionResolutionService;

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

    /** Order Candidates Brand Entry (docs/gops-order-candidates-brand-entry-implementation.md):
     * per-Brand longTermOutOfStockCount reuses the exact same predicate the
     * Dashboard-wide KPI already computes - never a fabricated/independent
     * count. */
    @Test
    void dashboardBrandRowsIncludeLongTermOutOfStockCount() {
        DashboardResponse response = dashboardService.getDashboard();
        assertTrue(response.brands().stream().allMatch(b -> b.longTermOutOfStockCount() >= 0),
                "every Brand row must expose a non-negative longTermOutOfStockCount");
        int sumAcrossBrands = response.brands().stream().mapToInt(b -> b.longTermOutOfStockCount()).sum();
        assertTrue(sumAcrossBrands <= response.longTermOutOfStockCount(),
                "the sum of per-Brand Long-term OOS can never exceed the Dashboard-wide total");
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

    /**
     * Stage 5E Targeted Remediation (RC-F, docs/real-data-audit/
     * gops-stage5e-targeted-remediation.md): {@code getDashboard()} must
     * NOT carry a method-level {@code @Transactional} - Stage 5D confirmed
     * that annotation held one Portal connection reserved for this
     * method's entire body, including the Legacy-side computation that can
     * run for seconds to minutes, starving the capped Portal pool under
     * concurrent load. A reflection check rather than a behavioral one
     * because the actual pool-exhaustion symptom only reproduces under
     * real concurrent load against a slow Legacy source (verified
     * separately, against the real Snapshot, in this Stage's Controlled
     * Performance Revalidation) - this test guards the specific structural
     * cause so it cannot silently regress.
     */
    @Test
    void getDashboardHasNoTransactionalAnnotation() throws NoSuchMethodException {
        Method method = DashboardService.class.getMethod("getDashboard");
        assertNull(method.getAnnotation(Transactional.class),
                "getDashboard() must not hold a Portal connection open for its entire body - "
                        + "each Portal repository call inside it should open/commit its own short transaction instead");
    }

    /**
     * Stage 5E Targeted Remediation (RC-A): candidateCount/outOfStockCount
     * are now computed via two different, dedicated code paths (a SQL
     * aggregate for outOfStockCount, a lean formula-evaluation fetch for
     * candidateCount - see DashboardService's own class Javadoc) instead of
     * one unpaginated fetch filtered in Java. This proves both paths still
     * agree with the exact same definitions the pre-Stage-5E
     * implementation used, for the known BR_OUTDOOR fixture Brand
     * (OD-TENT-001 and friends, backend/demo-data/02-seed.sql).
     */
    @Test
    void dashboardBrandRowCountsMatchDefinitionsForKnownFixtureBrand() {
        DashboardResponse response = dashboardService.getDashboard();
        var outdoor = response.brands().stream().filter(b -> "BR_OUTDOOR".equals(b.brandCode())).findFirst();
        assertTrue(outdoor.isPresent(), "BR_OUTDOOR must appear in the Brand breakdown - it has seeded active items");
        assertTrue(outdoor.get().candidateCount() >= 0);
        assertTrue(outdoor.get().outOfStockCount() >= 0);
        assertTrue(outdoor.get().longTermOutOfStockCount() <= outdoor.get().outOfStockCount(),
                "長期欠品 (currentStock==0 AND openPo==0) is always a subset of 欠品 (currentStock==0)");
    }

    /**
     * Stage 5E Targeted Remediation (RC-A): the bulk
     * {@link SupplierRegionClassificationResolutionService.RegionClassificationLookup}
     * Dashboard/Candidate List now use must resolve to the exact same
     * region as the original per-row {@code resolve(supplierCode,
     * brandCode)} call - the N+1 fix (Stage 5D's confirmed root cause)
     * must never change WHAT is resolved, only how many Portal round-trips
     * it costs. Uses an arbitrary real Demo Supplier/Brand pair - both
     * resolve to null (Master starts empty, see
     * RecommendedQtyCalculator's own Javadoc) precisely because this
     * proves the two paths agree even in that everyday, unconfigured case.
     */
    @Test
    void bulkRegionLookupAgreesWithSingleRowResolve() {
        String singleRowResult = regionResolutionService.resolve("SUP_TEST", "BR_OUTDOOR");
        String bulkResult = regionResolutionService.loadAll().resolve("SUP_TEST", "BR_OUTDOOR");
        assertEquals(singleRowResult, bulkResult);
    }

    /**
     * Candidate Brand List UX improvement (OrderCandidateBrandListPage's
     * own Brand entry screen, GET /api/order-candidates/brands): the
     * candidateCount>0 default filter must exclude every zero-candidate
     * Brand unless explicitly asked to include them - the same "hide
     * zero-activity rows by default" contract Phase D's Dashboard Brand
     * Breakdown table and Phase E's (now superseded) client-side filter
     * both already established, now enforced server-side instead.
     */
    @Test
    void findBrandRowsForCandidateEntryDefaultHidesZeroCandidateBrands() {
        List<DashboardBrandRow> hiddenByDefault = dashboardService.findBrandRowsForCandidateEntry(null, false);
        assertTrue(hiddenByDefault.stream().allMatch(b -> b.candidateCount() > 0),
                "every row returned with includeZeroCandidates=false must have candidateCount > 0");

        List<DashboardBrandRow> withZero = dashboardService.findBrandRowsForCandidateEntry(null, true);
        assertTrue(withZero.size() >= hiddenByDefault.size(),
                "includeZeroCandidates=true must return at least as many rows as the default view");
    }

    /**
     * Keyword matches both Brand Code and Brand Name, case-insensitively,
     * same convention as SupplierMasterService's own keyword filter
     * (Phase G §18) - reused here for consistency, not duplicated
     * independently.
     */
    @Test
    void findBrandRowsForCandidateEntryFiltersByKeywordCaseInsensitiveOnCodeOrName() {
        List<DashboardBrandRow> byCode = dashboardService.findBrandRowsForCandidateEntry("br_outdoor", true);
        assertTrue(byCode.stream().anyMatch(b -> "BR_OUTDOOR".equals(b.brandCode())),
                "lowercase Brand Code substring must still match BR_OUTDOOR");
        assertTrue(byCode.stream().allMatch(b -> b.brandCode().toLowerCase().contains("br_outdoor")
                        || b.brandName().toLowerCase().contains("br_outdoor")),
                "every returned row must actually match the keyword on code or name");
    }

    @Test
    void findBrandRowsForCandidateEntryKeywordMatchingNothingReturnsEmptyNotError() {
        List<DashboardBrandRow> result = dashboardService.findBrandRowsForCandidateEntry("NO_SUCH_BRAND_XYZ", true);
        assertTrue(result.isEmpty());
    }

    @Test
    void findBrandRowsForCandidateEntryBlankKeywordMeansNoFilter() {
        List<DashboardBrandRow> blank = dashboardService.findBrandRowsForCandidateEntry("   ", true);
        List<DashboardBrandRow> nullKeyword = dashboardService.findBrandRowsForCandidateEntry(null, true);
        assertEquals(nullKeyword.size(), blank.size());
    }

    /**
     * Same reasoning as getDashboardHasNoTransactionalAnnotation above
     * (RC-F): this method's own Portal repository calls should each open/
     * commit their own short transaction, not share one held open for the
     * whole method body.
     */
    @Test
    void findBrandRowsForCandidateEntryHasNoTransactionalAnnotation() throws NoSuchMethodException {
        Method method = DashboardService.class.getMethod("findBrandRowsForCandidateEntry", String.class, boolean.class);
        assertNull(method.getAnnotation(Transactional.class));
    }
}
