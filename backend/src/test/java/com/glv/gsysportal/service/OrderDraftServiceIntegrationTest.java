package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.UpdateDraftRequest;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.EmptySkuListException;
import com.glv.gsysportal.exception.InvalidOrderQtyException;
import com.glv.gsysportal.exception.MixedSupplierException;
import com.glv.gsysportal.exception.SkuNotFoundException;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Implementation instructions 16章 test list: Create Draft Transaction /
 * Rollback / Snapshot / Recommended-Qty-immutability / initial-Order-Qty /
 * Mixed-Supplier / Order-Qty=0 / Order-Qty-change / +-50% Warning / Audit /
 * Save / totals-recompute tests.
 *
 * Runs against the real local Legacy Demo MySQL + Prototype Postgres
 * (docker compose up -d), same as {@code LegacyReadOnlyIntegrationTest}.
 * The whole test method is wrapped in one Prototype-only transaction that is
 * always rolled back at the end (never committed), so no test data is ever
 * left behind in the local Postgres - {@link OrderDraftPersistenceService}'s
 * own {@code @Transactional(transactionManager = "prototypeTransactionManager")}
 * simply joins this outer transaction (default REQUIRED propagation).
 * The Legacy read side uses a separate transaction manager and is read-only
 * regardless.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OrderDraftServiceIntegrationTest {

    // Known Legacy Demo Instance fixture facts (backend/demo-data/02-seed.sql),
    // cross-checked manually via curl during this Step - see final report.
    private static final String SKU_TENT_1 = "OD-TENT-001"; // SUP_ALPHA, calc4 recommendedQty = 3
    private static final String SKU_TENT_2 = "OD-TENT-002"; // SUP_ALPHA, calc4 recommendedQty = 20
    private static final String SKU_BOWL_1 = "KT-BOWL-001"; // SUP_ALPHA, calc4 recommendedQty = 11
    private static final String SKU_RUG_1 = "HM-RUG-001";   // SUP_BETA (different supplier)

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderDraftPersistenceService persistenceService;
    @Autowired
    private AuditEventRepository auditEventRepository;

    // ---- Create Draft --------------------------------------------------

    @Test
    void createDraft_persistsSnapshotAndInitialOrderQtyEqualsRecommendedQty() {
        OrderDraftResponse response = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1, SKU_TENT_2), LocalDate.of(2026, 8, 27), LocalDate.of(2026, 9, 10), "test remark"),
                "tester01"
        );

        assertNotNull(response.id());
        assertTrue(response.draftNo().matches("DRAFT-\\d{8}-\\d{4}"));
        assertEquals("SUP_ALPHA", response.supplierCode());
        assertEquals(PortalOrder.STATUS_DRAFT, response.status());
        assertEquals(PortalOrder.DATA_SOURCE_DEMO_LEGACY, response.dataSource());
        assertEquals(2, response.details().size());

        var tentDetail = response.details().stream().filter(d -> d.sku().equals(SKU_TENT_1)).findFirst().orElseThrow();
        assertEquals(3, tentDetail.recommendedQty());
        // Requirements MD 13章: Order Qty = Recommended Qty at Create Draft time.
        assertEquals(tentDetail.recommendedQty(), tentDetail.orderQty());
        assertNotNull(tentDetail.itemName());
        assertNotNull(tentDetail.unitPrice());
        assertNotNull(tentDetail.currentStock());
        assertNotNull(tentDetail.leadTime());
    }

    @Test
    void createDraft_totalsMatchSumOfDetailAmounts() {
        OrderDraftResponse response = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1, SKU_TENT_2, SKU_BOWL_1), null, null, null),
                "tester01"
        );

        int expectedQty = response.details().stream().mapToInt(d -> d.orderQty()).sum();
        BigDecimal expectedAmount = response.details().stream()
                .map(d -> d.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(expectedQty, response.totalQty());
        assertEquals(0, expectedAmount.compareTo(response.totalAmount()));
    }

    @Test
    void createDraft_writesOrderDraftCreatedAuditEvent() {
        OrderDraftResponse response = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01"
        );

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(response.id());
        assertEquals(1, events.size());
        assertEquals(AuditEvent.ORDER_DRAFT_CREATED, events.get(0).getEventType());
        assertEquals("tester01", events.get(0).getPerformedBy());
        assertEquals(response.draftNo(), events.get(0).getNewValue());
    }

    @Test
    void createDraft_mixedSupplierIsRejected() {
        MixedSupplierException ex = assertThrows(MixedSupplierException.class, () ->
                orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1, SKU_RUG_1), null, null, null), "tester01")
        );
        assertEquals(Set.of("SUP_ALPHA", "SUP_BETA"), ex.supplierCodes());
    }

    @Test
    void createDraft_emptySkuListIsRejected() {
        assertThrows(EmptySkuListException.class, () ->
                orderDraftService.createDraft(new CreateDraftRequest(List.of(), null, null, null), "tester01")
        );
    }

    @Test
    void createDraft_unknownSkuIsRejected() {
        SkuNotFoundException ex = assertThrows(SkuNotFoundException.class, () ->
                orderDraftService.createDraft(new CreateDraftRequest(List.of("NO-SUCH-SKU"), null, null, null), "tester01")
        );
        assertTrue(ex.missingSkus().contains("NO-SUCH-SKU"));
    }

    /**
     * Deliberately NOT_SUPPORTED so the class-level outer transaction is
     * suspended for this test only: {@link OrderDraftPersistenceService#create}
     * must run in its own brand-new, independently-committing/-rolling-back
     * transaction for this to be a genuine Rollback Test (a REQUIRED join
     * into an already-open Postgres transaction cannot be queried again
     * after a DB-level constraint-violation abort without a savepoint, and
     * JpaTransactionManager does not have nested-transaction/savepoint
     * support enabled by default). No row is expected to persist, so there
     * is nothing to clean up afterwards.
     */
    @Test
    @Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    void createDraft_rollsBackEntirelyOnPartialFailure() {
        // Craft a Legacy row with a null item name to violate portal_order_detail's
        // item_name_snapshot NOT NULL constraint - proves that if any detail line
        // fails to persist, the whole Draft (header + all other lines) is rolled
        // back together, never left half-written (implementation instructions 6章).
        LegacyStockRow badRow = new LegacyStockRow(
                "BAD-SKU", null, "BR_KITCHEN", "Kitchen", "10", "NEW", false,
                5, 10, 1, 0, 0, 0, null, null, null, null,
                "SUP_ALPHA", "Alpha", BigDecimal.TEN, "JPY", null
        );

        long ordersBefore = portalOrderRepository.count();

        assertThrows(DataIntegrityViolationException.class, () ->
                persistenceService.create(new CreateDraftRequest(List.of("BAD-SKU"), null, null, null), List.of(badRow), "tester01")
        );

        assertEquals(ordersBefore, portalOrderRepository.count());
    }

    // ---- Update Draft ----------------------------------------------------

    @Test
    void updateDraft_orderQtyChangeWritesAuditAndRecomputesTotals() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1, SKU_TENT_2), null, null, null), "tester01"
        );
        var tentDetail = created.details().stream().filter(d -> d.sku().equals(SKU_TENT_1)).findFirst().orElseThrow();

        OrderDraftResponse updated = orderDraftService.updateDraft(
                created.id(),
                new UpdateDraftRequest(null, null, null, List.of(new UpdateDraftRequest.DetailQtyUpdate(tentDetail.id(), 5))),
                "tester02", true
        );

        var updatedTent = updated.details().stream().filter(d -> d.sku().equals(SKU_TENT_1)).findFirst().orElseThrow();
        assertEquals(5, updatedTent.orderQty());
        // Recommended Qty is a re-fetch-time Snapshot and must never change on Save.
        assertEquals(3, updatedTent.recommendedQty());

        int expectedTotalQty = updated.details().stream().mapToInt(d -> d.orderQty()).sum();
        assertEquals(expectedTotalQty, updated.totalQty());

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(created.id());
        assertTrue(events.stream().anyMatch(e ->
                e.getEventType().equals(AuditEvent.ORDER_QTY_CHANGED) && "tester02".equals(e.getPerformedBy())
                        && "3".equals(e.getOldValue()) && "5".equals(e.getNewValue())
        ));
    }

    @Test
    void updateDraft_warnsWhenOrderQtyDeviatesMoreThanFiftyPercent() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01"
        );
        var detail = created.details().get(0); // recommendedQty = 3

        OrderDraftResponse updated = orderDraftService.updateDraft(
                created.id(),
                new UpdateDraftRequest(null, null, null, List.of(new UpdateDraftRequest.DetailQtyUpdate(detail.id(), 1))), // ratio 1/3 < 0.5
                "tester02", true
        );

        assertTrue(updated.details().get(0).warningCodes().contains("ORDER_QTY_DIFFERS_SIGNIFICANTLY"));
        assertTrue(updated.warningCodes().contains("ORDER_QTY_DIFFERS_SIGNIFICANTLY"));
    }

    @Test
    void updateDraft_noWarningWhenOrderQtyWithinRange() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01"
        );
        var detail = created.details().get(0); // recommendedQty = 3

        OrderDraftResponse updated = orderDraftService.updateDraft(
                created.id(),
                new UpdateDraftRequest(null, null, null, List.of(new UpdateDraftRequest.DetailQtyUpdate(detail.id(), 4))), // ratio 4/3 ~= 1.33, within range
                "tester02", true
        );

        assertTrue(updated.details().get(0).warningCodes().isEmpty());
        assertTrue(updated.warningCodes().isEmpty());
    }

    @Test
    void updateDraft_orderQtyZeroIsAcceptedAndRecomputesAmountToZero() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01"
        );
        var detail = created.details().get(0);

        OrderDraftResponse updated = orderDraftService.updateDraft(
                created.id(),
                new UpdateDraftRequest(null, null, null, List.of(new UpdateDraftRequest.DetailQtyUpdate(detail.id(), 0))),
                "tester02", true
        );

        assertEquals(0, updated.details().get(0).orderQty());
        assertEquals(0, BigDecimal.ZERO.compareTo(updated.details().get(0).amount()));
    }

    @Test
    void updateDraft_negativeOrderQtyIsRejected() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01"
        );
        var detail = created.details().get(0);

        assertThrows(InvalidOrderQtyException.class, () -> orderDraftService.updateDraft(
                created.id(),
                new UpdateDraftRequest(null, null, null, List.of(new UpdateDraftRequest.DetailQtyUpdate(detail.id(), -1))),
                "tester02", true
        ));
    }

    @Test
    void updateDraft_unknownDetailIdIsSilentlyIgnored() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01"
        );

        OrderDraftResponse updated = orderDraftService.updateDraft(
                created.id(),
                new UpdateDraftRequest(null, null, null, List.of(new UpdateDraftRequest.DetailQtyUpdate(999999L, 99))),
                "tester02", true
        );

        assertEquals(created.details().get(0).orderQty(), updated.details().get(0).orderQty());
    }

    @Test
    void updateDraft_headerFieldChangesWriteAuditEvents() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), LocalDate.of(2026, 8, 27), LocalDate.of(2026, 9, 10), "before"), "tester01"
        );

        orderDraftService.updateDraft(
                created.id(),
                new UpdateDraftRequest(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 15), "after", null),
                "tester02", true
        );

        List<AuditEvent> events = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(created.id());
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals(AuditEvent.ORDER_DATE_CHANGED)));
        assertTrue(events.stream().anyMatch(e -> e.getEventType().equals(AuditEvent.REQUESTED_DELIVERY_CHANGED)));
        assertTrue(events.stream().anyMatch(e ->
                e.getEventType().equals(AuditEvent.REMARK_CHANGED) && "before".equals(e.getOldValue()) && "after".equals(e.getNewValue())
        ));
    }

    @Test
    void updateDraft_savedValuesMatchOnReGet() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, "orig"), "tester01"
        );
        var detail = created.details().get(0);

        orderDraftService.updateDraft(
                created.id(),
                new UpdateDraftRequest(null, null, "resaved", List.of(new UpdateDraftRequest.DetailQtyUpdate(detail.id(), 7))),
                "tester02", true
        );

        OrderDraftResponse reGet = orderDraftService.getDraft(created.id());
        assertEquals("resaved", reGet.remark());
        assertEquals(7, reGet.details().get(0).orderQty());
        assertEquals(7, reGet.totalQty());
    }

    // ---- Get Draft ---------------------------------------------------

    @Test
    void getDraft_notFoundThrows() {
        assertThrows(DraftNotFoundException.class, () -> orderDraftService.getDraft(-1L));
    }

    // ---- Delete Draft (G-OPS Operational Workflow Realignment Phase F
    // §16) - soft delete, DRAFT-only, no-downstream-process-started only. ----

    @Test
    void deleteDraft_softDeletesAndRecordsAuditEvent() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        orderDraftService.deleteDraft(created.id(), "tester01", false);

        // @SQLRestriction only affects actual SQL - within this single test
        // method's one Hibernate session, the just-modified entity is still
        // in the first-level (session) cache and would be returned from
        // there without a fresh query unless explicitly cleared. flush()
        // first is essential: clear() alone discards any not-yet-flushed
        // pending UPDATE along with the cached instance, which would make
        // this assertion pass for the wrong reason (re-reading the
        // ORIGINAL, still-non-deleted row, not a correctly-filtered one).
        // A real HTTP request's own separate transaction commits (flushing)
        // before any later request's fresh session reads it, so this
        // explicit flush()+clear() pair reproduces that real condition.
        entityManager.flush();
        entityManager.clear();

        assertThrows(DraftNotFoundException.class, () -> orderDraftService.getDraft(created.id()),
                "a soft-deleted Draft must become invisible to normal reads (@SQLRestriction)");
        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(created.id()).stream()
                .anyMatch(e -> AuditEvent.ORDER_DRAFT_DELETED.equals(e.getEventType())));
    }

    @Test
    void deleteDraft_actuallySetsDeletedAtAndDeletedByOnTheRow() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        orderDraftService.deleteDraft(created.id(), "admin-tester", true);

        // Bypass the Repository's own @SQLRestriction-filtered query methods
        // by going through the EntityManager directly, to verify this is a
        // real soft delete (row still physically present) and not an
        // accidental hard delete.
        PortalOrder raw = entityManager.find(PortalOrder.class, created.id());
        assertNotNull(raw, "the row must still physically exist - this is a SOFT delete");
    }

    @Test
    void deleteDraft_notFoundThrows() {
        assertThrows(DraftNotFoundException.class, () -> orderDraftService.deleteDraft(-1L, "tester01", false));
    }

    @Test
    void deleteDraft_refusedForNonCreatorNonAdmin() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> orderDraftService.deleteDraft(created.id(), "tester02", false),
                "same ownership rule as editing - only the creator or an ADMIN may delete");
    }

    @Test
    void deleteDraft_refusedOnceApproved() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");
        statusTransitionService.submitForApproval(created.id(), "tester01", false);
        statusTransitionService.approve(created.id(), "admin-tester");

        assertThrows(com.glv.gsysportal.exception.DraftDeletionNotAllowedException.class,
                () -> orderDraftService.deleteDraft(created.id(), "tester01", false));
    }

    /** The exact edge case this guard exists for: an Order returned to
     * DRAFT after already having a real Official PO Integration Request
     * from a prior approval cycle - status=DRAFT alone must NOT be treated
     * as "never touched downstream". */
    @Test
    void deleteDraft_refusedIfReturnedToDraftButAlreadyHasAnOfficialPoIntegrationRequest() {
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");
        statusTransitionService.submitForApproval(created.id(), "tester01", false);
        PortalOrder approved = statusTransitionService.approve(created.id(), "admin-tester");
        officialPoIntegrationService.requestIntegration(approved.getId(), "admin-tester");
        statusTransitionService.returnToDraft(approved.getId(), "admin-tester");

        OrderDraftResponse backToDraft = orderDraftService.getDraft(approved.getId());
        assertEquals(PortalOrder.STATUS_DRAFT, backToDraft.status(), "sanity check: really back in DRAFT status");

        assertThrows(com.glv.gsysportal.exception.DraftDeletionNotAllowedException.class,
                () -> orderDraftService.deleteDraft(approved.getId(), "tester01", false),
                "DRAFT status alone must not be trusted - this Order already has real Official PO Integration history");
    }

    @Autowired
    private com.glv.gsysportal.repository.prototype.PortalOrderRepository portalOrderRepository;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService officialPoIntegrationService;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
}
