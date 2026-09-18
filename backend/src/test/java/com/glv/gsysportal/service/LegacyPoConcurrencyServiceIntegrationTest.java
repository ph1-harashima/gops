package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.CreateRevisionRequest;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.response.LegacyPoBaselineResponse;
import com.glv.gsysportal.dto.response.LegacyPoConcurrencyResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.IntegrationRequestRequiredException;
import com.glv.gsysportal.exception.InvalidIntegrationIntentException;
import com.glv.gsysportal.exception.LegacyPoNotFoundForBaselineException;
import com.glv.gsysportal.exception.OfficialPoNotLinkedException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
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
 * Phase 7-C6 26章 Heavy Regression: Capture/Compare against real Legacy Demo
 * MySQL Test Fixtures (docs/excel-legacy-concurrency-control.md 5章 -
 * PO-CONC-01/02/03). Same whole-test-method Prototype transaction + rollback
 * pattern as the other Step 2+ integration tests. Legacy is NEVER written to
 * by any test in this class (READ ONLY throughout) - the CHANGED/LINE_ADDED/
 * LINE_REMOVED scenarios that DO require mutating Legacy Demo MySQL fixture
 * data live at the E2E layer instead (7-C6 14章's explicitly sanctioned
 * Test-only Legacy Demo DB mutation via `docker exec`, never through this
 * application's own READ ONLY connection - see
 * frontend/e2e/legacy-po-concurrency-control.spec.ts).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class LegacyPoConcurrencyServiceIntegrationTest {

    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin-tester";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private LegacyPoConcurrencyService concurrencyService;
    @Autowired
    private SupplierResponseService supplierResponseService;
    @Autowired
    private OrderRevisionService orderRevisionService;
    @Autowired
    private PortalOrderRepository portalOrderRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private PortalOrder createApprovedOrder(String sku) {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(sku), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        return statusTransitionService.approve(draft.id(), ADMIN);
    }

    /** Test-only officialPoNo linkage (same idiom as
     * FulfillmentServiceIntegrationTest.linkToOfficialPo). */
    private PortalOrder linkToOfficialPo(PortalOrder order, String officialPoNo) {
        order.setOfficialPoNo(officialPoNo);
        return portalOrderRepository.save(order);
    }

    @Test
    void compareOnFreshDraftIsNotLinked() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of("OD-TENT-001"), null, null, null), OPERATOR);

        LegacyPoConcurrencyResponse response = concurrencyService.compare(draft.id());

        assertEquals(LegacyPoConcurrencyResponse.RESULT_NOT_LINKED, response.result());
        assertTrue(response.diffs().isEmpty());
    }

    @Test
    void compareWhenLegacyPoDoesNotExistIsPoNotFound() {
        PortalOrder order = linkToOfficialPo(createApprovedOrder("OD-TENT-001"), "PO-DOES-NOT-EXIST-IN-LEGACY");

        LegacyPoConcurrencyResponse response = concurrencyService.compare(order.getId());

        assertEquals(LegacyPoConcurrencyResponse.RESULT_PO_NOT_FOUND, response.result());
    }

    @Test
    void compareWhenNoBaselineCapturedYetIsNotBaselined() {
        PortalOrder order = linkToOfficialPo(createApprovedOrder("OD-TENT-001"), "PO-CONC-01");

        LegacyPoConcurrencyResponse response = concurrencyService.compare(order.getId());

        assertEquals(LegacyPoConcurrencyResponse.RESULT_NOT_BASELINED, response.result());
    }

    @Test
    void captureBaselineRequiresOfficialPoNo() {
        PortalOrder order = createApprovedOrder("OD-TENT-001");

        assertThrows(OfficialPoNotLinkedException.class, () -> concurrencyService.captureBaseline(order.getId(), ADMIN));
    }

    @Test
    void captureBaselineRequiresAnExistingIntegrationRequest() {
        PortalOrder order = linkToOfficialPo(createApprovedOrder("OD-TENT-001"), "PO-CONC-01");
        // No requestIntegration() call - no Integration Request row exists yet.

        assertThrows(IntegrationRequestRequiredException.class, () -> concurrencyService.captureBaseline(order.getId(), ADMIN));
    }

    @Test
    void captureBaselineRequiresLegacyPoToExist() {
        PortalOrder order = createApprovedOrder("OD-TENT-001");
        integrationService.requestIntegration(order.getId(), ADMIN);
        linkToOfficialPo(order, "PO-DOES-NOT-EXIST-IN-LEGACY");

        assertThrows(LegacyPoNotFoundForBaselineException.class, () -> concurrencyService.captureBaseline(order.getId(), ADMIN));
    }

    @Test
    void captureThenCompareImmediatelyIsUnchanged() {
        PortalOrder order = createApprovedOrder("OD-TENT-001");
        integrationService.requestIntegration(order.getId(), ADMIN);
        linkToOfficialPo(order, "PO-CONC-01");

        LegacyPoBaselineResponse captured = concurrencyService.captureBaseline(order.getId(), ADMIN);
        assertEquals(1, captured.revisionNo());
        assertEquals("PO-CONC-01", captured.officialPoNo());
        assertEquals(64, captured.fingerprint().length());

        LegacyPoConcurrencyResponse compared = concurrencyService.compare(order.getId());

        assertEquals(LegacyPoConcurrencyResponse.RESULT_UNCHANGED, compared.result());
        assertEquals(captured.fingerprint(), compared.baselineFingerprint());
        assertTrue(compared.diffs().isEmpty());
    }

    @Test
    void captureBaselineAlwaysAudits() {
        PortalOrder order = createApprovedOrder("OD-TENT-001");
        integrationService.requestIntegration(order.getId(), ADMIN);
        linkToOfficialPo(order, "PO-CONC-01");

        concurrencyService.captureBaseline(order.getId(), ADMIN);
        concurrencyService.captureBaseline(order.getId(), ADMIN); // re-capture is allowed

        long count = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .filter(e -> AuditEvent.LEGACY_PO_BASELINE_CAPTURED.equals(e.getEventType())).count();
        assertEquals(2, count, "every Capture call Audits, including re-captures (7-C6 9章)");
    }

    @Test
    void compareNeverAuditsOnUnchanged() {
        PortalOrder order = createApprovedOrder("OD-TENT-001");
        integrationService.requestIntegration(order.getId(), ADMIN);
        linkToOfficialPo(order, "PO-CONC-01");
        concurrencyService.captureBaseline(order.getId(), ADMIN);

        concurrencyService.compare(order.getId());
        concurrencyService.compare(order.getId());

        long count = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .filter(e -> AuditEvent.LEGACY_PO_CHANGE_DETECTED.equals(e.getEventType())).count();
        assertEquals(0, count, "UNCHANGED must never Audit (7-C6 18章)");
    }

    /** Phase 7-C6 16章's central regression: a Baseline captured for Revision
     * 1 must NEVER be silently reused once the Order has moved on to
     * targeting Revision 2. */
    @Test
    void revision1BaselineIsNeverReusedForRevision2() {
        // Revision Consistency Audit (docs/gulliver-phase1-revision-consistency-audit.md):
        // this test previously asserted the exact bug that Audit fixed - it
        // treated a plain demoSend (no actual correction/Reissue) as if it
        // had already advanced the Order to "Revision 2", and expected
        // compare() to (wrongly) report NOT_BASELINED at that point. Under
        // the corrected model, a demoSend alone never creates a new
        // Official PO Document Revision - only an actual Reissue does (after
        // a genuine Supplier Response correction cycle) - so a Revision 1
        // Baseline correctly still satisfies Compare after a plain demoSend.
        // This test now drives a REAL Revision 2 into existence (the same
        // correction+Reissue cycle OfficialPoReissueIntegrationTest's own
        // fullReissueCycle test uses) before asserting NOT_BASELINED,
        // proving the "never reused across a genuine Revision change" claim
        // against an actual Revision 2, not a simulated one.
        PortalOrder order = createApprovedOrder("OD-TENT-001");
        integrationService.requestIntegration(order.getId(), ADMIN); // targets Revision 1
        // isReissueRequired (and therefore reissue() below) requires the
        // current Document to have actually been issued (GENERATED or
        // later) - confirm the PO No. and generate the Excel so Revision 1
        // reaches that state, matching OfficialPoReissueIntegrationTest's
        // own fullReissueCycle_... precondition.
        integrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest("PO-CONC-01", "WK36", "2026-09-05", null, null, null), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);
        concurrencyService.captureBaseline(order.getId(), ADMIN); // Revision 1 Baseline

        LegacyPoConcurrencyResponse comparedBeforeReissue = concurrencyService.compare(order.getId());
        assertEquals(LegacyPoConcurrencyResponse.RESULT_UNCHANGED, comparedBeforeReissue.result(),
                "a plain Demo Send with no correction must not invalidate Revision 1's own Baseline");

        // Send, get a Supplier Response with a different Qty, correct the
        // Order, re-approve, then Reissue - only THIS sequence actually
        // creates Revision 2 (mirrors OfficialPoReissueIntegrationTest's own
        // fullReissueCycle_... test).
        PortalOrder sent = statusTransitionService.demoSend(order.getId(), ADMIN);
        Long detailId = supplierResponseService.getSupplierResponse(sent.getId()).details().get(0).detailId();
        supplierResponseService.saveSupplierResponse(sent.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(new SaveSupplierResponseRequest.LineUpdate(detailId, 2, null, null, null))),
                ADMIN);
        supplierResponseService.confirmSupplierResponse(sent.getId(), ADMIN);
        orderRevisionService.createCorrection(sent.getId(),
                new CreateRevisionRequest("Supplier can only supply 2", true), ADMIN);
        statusTransitionService.submitForApproval(sent.getId(), OPERATOR, false);
        PortalOrder reapproved = statusTransitionService.approve(sent.getId(), ADMIN);
        integrationService.reissue(reapproved.getId(), ADMIN); // creates the real Revision 2

        LegacyPoConcurrencyResponse comparedAfterReissue = concurrencyService.compare(reapproved.getId());

        assertEquals(LegacyPoConcurrencyResponse.RESULT_NOT_BASELINED, comparedAfterReissue.result(),
                "Revision 1's Baseline must not silently satisfy Revision 2's Compare");
    }

    @Test
    void canProceedToHandoffIsFalseWithoutBaseline() {
        PortalOrder order = createApprovedOrder("OD-TENT-001");
        integrationService.requestIntegration(order.getId(), ADMIN);
        linkToOfficialPo(order, "PO-CONC-01");

        assertFalse(concurrencyService.canProceedToHandoff(order.getId()));
    }

    @Test
    void canProceedToHandoffIsTrueWhenPreflightPassesAndConcurrencyUnchanged() {
        PortalOrder order = createApprovedOrder("OD-TENT-001");
        integrationService.requestIntegration(order.getId(), ADMIN); // PASS (Supplier/Brand/Item all exist)
        linkToOfficialPo(order, "PO-CONC-01");
        concurrencyService.captureBaseline(order.getId(), ADMIN);

        assertTrue(concurrencyService.canProceedToHandoff(order.getId()));
    }

    @Test
    void setIntegrationIntentRejectsInvalidValue() {
        PortalOrder order = createApprovedOrder("OD-TENT-001");
        integrationService.requestIntegration(order.getId(), ADMIN);

        assertThrows(InvalidIntegrationIntentException.class,
                () -> integrationService.setIntegrationIntent(order.getId(), "BOGUS", ADMIN));
    }

    @Test
    void setIntegrationIntentRequiresExistingIntegrationRequest() {
        PortalOrder order = createApprovedOrder("OD-TENT-001");
        // No requestIntegration() call.

        assertThrows(IntegrationRequestRequiredException.class,
                () -> integrationService.setIntegrationIntent(order.getId(), "NEW", ADMIN));
    }

    @Test
    void setIntegrationIntentPersistsNewOrUpdate() {
        PortalOrder order = createApprovedOrder("OD-TENT-001");
        integrationService.requestIntegration(order.getId(), ADMIN);

        var response = integrationService.setIntegrationIntent(order.getId(), "NEW", ADMIN);

        assertEquals("NEW", response.integrationIntent());
    }

    @Test
    void compareOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> concurrencyService.compare(999_999L));
    }

    @Test
    void captureBaselineOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> concurrencyService.captureBaseline(999_999L, ADMIN));
    }
}
