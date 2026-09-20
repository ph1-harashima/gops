package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.UpdateDraftRequest;
import com.glv.gsysportal.dto.response.OfficialPoImportConfirmationResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.OfficialPoNotSubmittedException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 9-C: G-SYS Import Confirmation - real Legacy READ ONLY checks
 * against the same "PO-CONC-01" Test Fixture (CC-ITEM-001 qty 5,
 * CC-ITEM-002 qty 2, STATUS=OFFICIAL) {@code LegacyPoConcurrencyServiceIntegrationTest}
 * already relies on (docs/excel-legacy-concurrency-control.md 5章). Same
 * whole-test-method Prototype transaction + rollback pattern - Legacy is
 * NEVER written to (READ ONLY throughout).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OfficialPoImportConfirmationIntegrationTest {

    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin-tester";
    private static final String FIXTURE_PO_NO = "PO-CONC-01";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private OfficialPoIntegrationRequestRepository integrationRequestRepository;
    @Autowired
    private PortalOrderRepository portalOrderRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @PersistenceContext
    private EntityManager entityManager;

    /** Approves an Order, requests Integration, and directly marks the
     * Integration Request SUBMITTED with the given officialPoNo (Phase 9-B's
     * own placement path is exercised elsewhere -
     * OfficialPoImportFolderPlacementIntegrationTest - this test is scoped
     * to Confirmation only, same "advance the State Machine directly"
     * idiom {@code OfficialPoNumberAndExcelGenerationIntegrationTest.confirmIsLockedOnceSubmitted}
     * already uses). */
    private PortalOrder submittedOrder(List<String> skus, String officialPoNo) {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(skus, null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN);
        return markSubmittedDirect(order, officialPoNo);
    }

    /** Same as {@link #submittedOrder} but sets each line's orderQty (via
     * the exact-quantity Fixture map) BEFORE Submit-for-Approval - required
     * because CC-ITEM-001/002 default to a 0 recommendedQty, which
     * {@code PoPreviewValidator} rejects at Submit time. */
    private PortalOrder submittedOrderWithExactQty(java.util.Map<String, Integer> skuToQty, String officialPoNo) {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.copyOf(skuToQty.keySet()), null, null, null), OPERATOR);
        var updates = draft.details().stream()
                .map(d -> new UpdateDraftRequest.DetailQtyUpdate(d.id(), skuToQty.get(d.sku())))
                .toList();
        orderDraftService.updateDraft(draft.id(), new UpdateDraftRequest(null, null, null, updates), OPERATOR, false);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN);
        return markSubmittedDirect(order, officialPoNo);
    }

    /** Post-Freeze Technical Stability Audit
     * (docs/gops-post-freeze-e2e-stability-audit.md §6/§17) - releases any
     * pre-existing claim on this literal officialPoNo before taking it,
     * within this test method's own transaction (rolled back at test end,
     * so nothing is permanently altered). See
     * LegacyPoConcurrencyServiceIntegrationTest.linkToOfficialPo's fuller
     * comment for why this is needed: the same "PO-CONC-01" literal is also
     * claimed - permanently, via a real committed row - by
     * frontend/e2e/legacy-po-concurrency-control.spec.ts. */
    private PortalOrder markSubmittedDirect(PortalOrder order, String officialPoNo) {
        entityManager.createNativeQuery("UPDATE portal_order SET official_po_no = NULL WHERE official_po_no = :poNo AND id <> :id")
                .setParameter("poNo", officialPoNo)
                .setParameter("id", order.getId())
                .executeUpdate();
        order.setOfficialPoNo(officialPoNo);
        portalOrderRepository.save(order);

        OfficialPoIntegrationRequest request = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(order.getId(), 1).orElseThrow();
        request.setOfficialPoNo(officialPoNo);
        OffsetDateTime now = OffsetDateTime.now();
        request.markGenerated("test-file-key", now);
        request.markSubmitted(now);
        integrationRequestRepository.save(request);
        return order;
    }

    @Test
    void confirmBeforeSubmittedThrows() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of("OD-TENT-001"), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN);

        assertThrows(OfficialPoNotSubmittedException.class,
                () -> integrationService.confirmImport(order.getId(), ADMIN));
    }

    @Test
    void confirmWhenLegacyHasNoMatchingPoIsNotYetImported() {
        PortalOrder order = submittedOrder(List.of("OD-TENT-001"), "PO-DOES-NOT-EXIST-IN-LEGACY-9C");

        OfficialPoImportConfirmationResponse response = integrationService.confirmImport(order.getId(), ADMIN);

        assertFalse(response.matched());
        assertEquals(OfficialPoImportConfirmationResponse.REASON_NOT_YET_IMPORTED, response.reason());
        assertEquals("SUBMITTED", response.integration().status(), "no state change on NOT_YET_IMPORTED");
    }

    @Test
    void confirmWhenLegacyLinesDontMatchIsMismatch() {
        // OD-TENT-001 has nothing to do with the PO-CONC-01 Fixture's real
        // lines (CC-ITEM-001/CC-ITEM-002) - guaranteed mismatch.
        PortalOrder order = submittedOrder(List.of("OD-TENT-001"), FIXTURE_PO_NO);

        OfficialPoImportConfirmationResponse response = integrationService.confirmImport(order.getId(), ADMIN);

        assertFalse(response.matched());
        assertEquals(OfficialPoImportConfirmationResponse.REASON_MISMATCH, response.reason());
        assertFalse(response.details().isEmpty());
        assertEquals("SUBMITTED", response.integration().status(), "no state change on MISMATCH");
    }

    @Test
    void confirmWhenLinesMatchTransitionsToConfirmed() {
        PortalOrder order = submittedOrderWithExactQty(
                java.util.Map.of("CC-ITEM-001", 5, "CC-ITEM-002", 2), FIXTURE_PO_NO);

        OfficialPoImportConfirmationResponse response = integrationService.confirmImport(order.getId(), ADMIN);

        assertTrue(response.matched());
        assertEquals("CONFIRMED", response.integration().status());

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        assertTrue(trail.stream().anyMatch(e -> AuditEvent.OFFICIAL_PO_IMPORT_CONFIRMED.equals(e.getEventType())));
    }

    @Test
    void confirmOnAlreadyConfirmedIsIdempotentNoOp() {
        PortalOrder order = submittedOrderWithExactQty(
                java.util.Map.of("CC-ITEM-001", 5, "CC-ITEM-002", 2), FIXTURE_PO_NO);

        integrationService.confirmImport(order.getId(), ADMIN);
        OfficialPoImportConfirmationResponse second = integrationService.confirmImport(order.getId(), ADMIN);

        assertTrue(second.matched());
        assertEquals("CONFIRMED", second.integration().status());
        long confirmedAuditCount = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_IMPORT_CONFIRMED.equals(e.getEventType())).count();
        assertEquals(1, confirmedAuditCount, "re-confirming an already-CONFIRMED Request must not re-Audit");
    }
}
