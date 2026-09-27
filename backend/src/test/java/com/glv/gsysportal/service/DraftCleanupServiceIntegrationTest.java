package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.UpdateDraftRequest;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Draft Lifecycle Cleanup Audit - implementation test suite (letters A-M
 * are exactly this Stage's own requested scenarios). {@code @Transactional}
 * (Portal-only, same convention every other Integration Test in this
 * package already uses) means every Draft created here is rolled back at
 * the end of its own test method - this Stage's own instruction (§12) that
 * testing must never leave new rows behind in the shared local Postgres.
 *
 * <p>Uses the real {@link OrderDraftService#createDraft} (Legacy READ ONLY
 * re-fetch + Prototype write, unchanged) against the real seeded Demo Data
 * SKU {@code OD-TENT-001}, then backdates {@code created_at} directly via
 * the Repository where a test needs to simulate "created N days ago" -
 * {@code createDraft} itself has no caller-controlled creation timestamp
 * (implementation instructions 4章: Order Date defaults to "today", not a
 * client-supplied audit timestamp).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class DraftCleanupServiceIntegrationTest {

    private static final String SKU = "OD-TENT-001";
    private static final String OPERATOR = "tester01";

    @Autowired
    private DraftCleanupService draftCleanupService;
    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService orderStatusTransitionService;
    @Autowired
    private PoPreviewService poPreviewService;
    @Autowired
    private OrderHistoryService orderHistoryService;
    @Autowired
    private PortalOrderRepository portalOrderRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private OfficialPoIntegrationRequestRepository integrationRequestRepository;

    private Long newDraftId() {
        return orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU), null, null, null), OPERATOR).id();
    }

    private void backdate(Long id, int daysAgo) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow();
        order.setCreatedAt(OffsetDateTime.now().minusDays(daysAgo));
        portalOrderRepository.save(order);
    }

    private static OffsetDateTime sevenDayCutoff() {
        return OffsetDateTime.now().minusDays(7);
    }

    /** A: ORDER_DRAFT_CREATEDのみ かつ 7日超 -> cleanup対象。 */
    @Test
    void untouchedDraftOlderThanCutoffIsACandidate() {
        Long id = newDraftId();
        backdate(id, 8);
        assertTrue(draftCleanupService.findCandidateIds(sevenDayCutoff()).contains(id));
    }

    /** B: ORDER_DRAFT_CREATEDのみ かつ 7日未満 -> 対象外。 */
    @Test
    void untouchedDraftWithinRetentionIsNotACandidate() {
        Long id = newDraftId(); // createdAt = now, well within the 7-day window
        assertFalse(draftCleanupService.findCandidateIds(sevenDayCutoff()).contains(id));
    }

    /** C: ORDER_QTY_CHANGEDあり -> 対象外。 */
    @Test
    void draftWithOrderQtyChangeIsExcluded() {
        Long id = newDraftId();
        backdate(id, 8);
        OrderDraftResponse draft = orderDraftService.getDraft(id);
        Long detailId = draft.details().get(0).id();
        orderDraftService.updateDraft(id, new UpdateDraftRequest(null, null, null,
                        List.of(new UpdateDraftRequest.DetailQtyUpdate(detailId, draft.details().get(0).orderQty() + 1))),
                OPERATOR, false);
        assertFalse(draftCleanupService.findCandidateIds(sevenDayCutoff()).contains(id));
    }

    /**
     * D: FOLLOW_UP_CREATEDあり -> 対象外。 This is exactly the corrected
     * condition from this Stage's own §2 - a narrower "4 field-change event
     * types only" filter would have missed this real case.
     */
    @Test
    void draftWithFollowUpActivityIsExcluded() {
        Long id = newDraftId();
        backdate(id, 8);
        auditEventRepository.save(new AuditEvent(id, null, AuditEvent.FOLLOW_UP_CREATED,
                null, null, null, "system", OffsetDateTime.now()));
        assertFalse(draftCleanupService.findCandidateIds(sevenDayCutoff()).contains(id));
    }

    /** E: revisionあり -> 対象外。 */
    @Test
    void draftWithRevisionIsExcluded() {
        Long id = newDraftId();
        backdate(id, 8);
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow();
        order.setCurrentRevisionNo(1);
        portalOrderRepository.save(order);
        assertFalse(draftCleanupService.findCandidateIds(sevenDayCutoff()).contains(id));
    }

    /** F: Official PO Integration Requestあり -> 対象外。 */
    @Test
    void draftWithOfficialPoIntegrationRequestIsExcluded() {
        Long id = newDraftId();
        backdate(id, 8);
        OffsetDateTime now = OffsetDateTime.now();
        OfficialPoIntegrationRequest req = new OfficialPoIntegrationRequest();
        req.setPortalOrderId(id);
        req.setRevisionNo(1);
        req.setStatus(OfficialPoIntegrationRequest.STATUS_PENDING);
        req.setRequestedBy(OPERATOR);
        req.setRequestedAt(now);
        req.setCreatedAt(now);
        req.setUpdatedAt(now);
        req.setLifecycleStatus(OfficialPoIntegrationRequest.LIFECYCLE_ACTIVE);
        req.setSignatureStatus(OfficialPoIntegrationRequest.SIGNATURE_NOT_REQUIRED);
        integrationRequestRepository.save(req);
        assertFalse(draftCleanupService.findCandidateIds(sevenDayCutoff()).contains(id));
    }

    /**
     * G: source_order_idあり -> 対象外。 {@code source_order_id} carries a
     * real FK to another {@code portal_order} row (Reorder Foundation), so
     * the referenced Order must genuinely exist - a second real Draft
     * stands in for "the Order this one was reordered from".
     */
    @Test
    void reorderOriginDraftIsExcluded() {
        Long originalOrderId = newDraftId();
        Long id = newDraftId();
        backdate(id, 8);
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow();
        order.setSourceOrderId(originalOrderId);
        portalOrderRepository.save(order);
        assertFalse(draftCleanupService.findCandidateIds(sevenDayCutoff()).contains(id));
    }

    /** H: 既にdeleted_atあり -> 対象外。 */
    @Test
    void alreadySoftDeletedDraftIsExcluded() {
        Long id = newDraftId();
        backdate(id, 8);
        orderDraftService.deleteDraft(id, OPERATOR, false);
        assertFalse(draftCleanupService.findCandidateIds(sevenDayCutoff()).contains(id));
    }

    /** I: cleanup成功 -> soft deleteされる -> 既存deleteDraftのaudit eventも正しく残る。 */
    @Test
    void eligibleDraftIsSoftDeletedAndAuditEventRecorded() {
        Long id = newDraftId();
        backdate(id, 8);

        DraftCleanupService.CleanupOutcome outcome = draftCleanupService.cleanupOneIfStillEligible(id, sevenDayCutoff());
        assertEquals(DraftCleanupService.CleanupOutcome.DELETED, outcome);

        assertThrows(DraftNotFoundException.class, () -> orderDraftService.getDraft(id));

        boolean hasDeleteEvent = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(id).stream()
                .anyMatch(e -> AuditEvent.ORDER_DRAFT_DELETED.equals(e.getEventType())
                        && DraftCleanupService.SYSTEM_PERFORMED_BY.equals(e.getPerformedBy()));
        assertTrue(hasDeleteEvent, "the exact same ORDER_DRAFT_DELETED audit event deleteDraft() always writes must be present");
    }

    /**
     * J: soft delete後 -> Approval不可。 Also the regression proof for the
     * §5/§9 deleted_at gap fix - before that fix, this call would have
     * succeeded (findById bypasses @SQLRestriction) despite the Draft being
     * soft-deleted.
     */
    @Test
    void softDeletedDraftCannotBeSubmittedForApproval() {
        Long id = newDraftId();
        backdate(id, 8);
        draftCleanupService.cleanupOneIfStillEligible(id, sevenDayCutoff());

        assertThrows(DraftNotFoundException.class,
                () -> orderStatusTransitionService.submitForApproval(id, OPERATOR, false));
    }

    /** K: soft delete後 -> Order Detail直接アクセス不可(§5/§9 gap fix regression proof). */
    @Test
    void softDeletedDraftDetailIsNotAccessible() {
        Long id = newDraftId();
        backdate(id, 8);
        draftCleanupService.cleanupOneIfStillEligible(id, sevenDayCutoff());

        assertThrows(DraftNotFoundException.class, () -> orderHistoryService.detail(id));
    }

    /** L: soft delete後 -> PoPreview等の後続workflow不可(§5/§9 gap fix regression proof). */
    @Test
    void softDeletedDraftPreviewIsNotAccessible() {
        Long id = newDraftId();
        backdate(id, 8);
        draftCleanupService.cleanupOneIfStillEligible(id, sevenDayCutoff());

        assertThrows(DraftNotFoundException.class, () -> poPreviewService.preview(id));
    }

    /**
     * M: cleanup候補抽出後にユーザー更新が入ったrace condition -> cleanupされない
     * (§7). Reproduces the exact 12:00:00/12:00:01/12:00:02 timeline from
     * this Stage's own instruction: candidate selected, THEN the user
     * saves, THEN cleanup's own per-item execution runs against the (now
     * stale) candidate id - it must re-verify and skip, never delete.
     */
    @Test
    void raceConditionUserSavesBetweenSelectionAndCleanupPreventsDeletion() {
        Long id = newDraftId();
        backdate(id, 8);
        OffsetDateTime cutoff = sevenDayCutoff();

        // 12:00:00 - candidate SELECT.
        List<Long> candidates = draftCleanupService.findCandidateIds(cutoff);
        assertTrue(candidates.contains(id), "must start out as a genuine candidate");

        // 12:00:01 - the user saves (a real edit, via the real Update path).
        OrderDraftResponse draft = orderDraftService.getDraft(id);
        Long detailId = draft.details().get(0).id();
        orderDraftService.updateDraft(id, new UpdateDraftRequest(null, null, "resuming work on this",
                        List.of(new UpdateDraftRequest.DetailQtyUpdate(detailId, draft.details().get(0).orderQty() + 1))),
                OPERATOR, false);

        // 12:00:02 - cleanup executes against the now-stale candidate id.
        DraftCleanupService.CleanupOutcome outcome = draftCleanupService.cleanupOneIfStillEligible(id, cutoff);
        assertEquals(DraftCleanupService.CleanupOutcome.SKIPPED_HAS_ACTIVITY, outcome);

        PortalOrder stillThere = portalOrderRepository.findById(id).orElseThrow();
        assertNull(stillThere.getDeletedAt(), "the user's just-saved Draft must survive cleanup");
    }

    /** Sanity: the dry-run entry point itself is genuinely read-only. */
    @Test
    void findCandidateIdsNeverModifiesAnything() {
        Long id = newDraftId();
        backdate(id, 8);
        draftCleanupService.findCandidateIds(sevenDayCutoff());
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow();
        assertNull(order.getDeletedAt());
        assertEquals(PortalOrder.STATUS_DRAFT, order.getStatus());
    }
}
