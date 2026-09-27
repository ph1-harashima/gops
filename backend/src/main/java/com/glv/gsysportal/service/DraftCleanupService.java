package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.exception.DraftDeletionNotAllowedException;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Draft Lifecycle Cleanup Audit (this Stage's own investigation, §1-§11):
 * abandoned-Draft cleanup, Option B ("background scheduled job + reuse of
 * the existing {@link OrderDraftService#deleteDraft}"). The current
 * create-on-click Draft lifecycle Architecture (Phase F, commit 6233cec,
 * Option C) is NOT changed by this class - it only adds a way to soft-
 * delete a Draft that has genuinely never been touched, on a delay, in
 * addition to the existing manual per-Draft delete this class's own
 * {@link #cleanupOneIfStillEligible} verbatim reuses.
 *
 * <p>Candidate condition (audit §3/§6, corrected in §2 after finding a
 * narrower "4 field-change event types only" filter missed real Orders
 * with FOLLOW_UP_* activity):
 * <pre>
 *   status = DRAFT
 *   AND deleted_at IS NULL                 (via @SQLRestriction, always)
 *   AND current_revision_no IS NULL
 *   AND source_order_id IS NULL            (never a Reorder-origin Draft)
 *   AND no Official PO Integration Request exists for this Order
 *   AND no audit_event other than ORDER_DRAFT_CREATED exists for this Order
 *   AND created_at &lt; cutoff
 * </pre>
 *
 * <p>Two-phase, defense-in-depth design (audit §4/§7):
 * <ol>
 *   <li>{@link #findCandidateIds} - a cheap, read-only pass, safe to call
 *       for a dry-run count with zero side effects.</li>
 *   <li>{@link #cleanupOneIfStillEligible} - re-verifies the ENTIRE
 *       candidate condition again, under a pessimistic row lock
 *       ({@link PortalOrderRepository#findByIdForUpdate}), immediately
 *       before calling the real, already-tested {@code deleteDraft} - so a
 *       Draft the user started genuinely working on between phase 1 and
 *       phase 2 (Save, Submit for Approval, or a prior manual Delete) is
 *       never removed. This is why the pre-filter in phase 1 is allowed to
 *       be cheap/approximate in principle - phase 2 is the actual safety
 *       gate, not phase 1.</li>
 * </ol>
 */
@Service
public class DraftCleanupService {

    /** system-initiated, never a real user - see OrderDraftService.deleteDraft's
     * own "creator or ADMIN" check, satisfied here via performerIsAdmin=true. */
    public static final String SYSTEM_PERFORMED_BY = "system-draft-cleanup";

    public enum CleanupOutcome {
        DELETED,
        SKIPPED_ALREADY_GONE,
        SKIPPED_STATUS_CHANGED,
        SKIPPED_HAS_REVISION,
        SKIPPED_HAS_REORDER_SOURCE,
        SKIPPED_TOO_RECENT,
        SKIPPED_HAS_OFFICIAL_PO,
        SKIPPED_HAS_ACTIVITY
    }

    private final PortalOrderRepository portalOrderRepository;
    private final AuditEventRepository auditEventRepository;
    private final OfficialPoIntegrationRequestRepository integrationRequestRepository;
    private final OrderDraftService orderDraftService;
    private final long retentionDays;

    public DraftCleanupService(PortalOrderRepository portalOrderRepository,
                                AuditEventRepository auditEventRepository,
                                OfficialPoIntegrationRequestRepository integrationRequestRepository,
                                OrderDraftService orderDraftService,
                                @Value("${app.draft-cleanup.retention-days:7}") long retentionDays) {
        this.portalOrderRepository = portalOrderRepository;
        this.auditEventRepository = auditEventRepository;
        this.integrationRequestRepository = integrationRequestRepository;
        this.orderDraftService = orderDraftService;
        this.retentionDays = retentionDays;
    }

    /** {@code created_at < currentCutoff()} is the retention-days boundary,
     * computed fresh from the configured {@code app.draft-cleanup.retention-days}
     * (default 7) every time it is called - never a magic number inline. */
    public OffsetDateTime currentCutoff() {
        return OffsetDateTime.now().minusDays(retentionDays);
    }

    /**
     * Phase 1 (audit §3/§6/§10): read-only, safe to call as a dry-run with
     * zero side effects - this is exactly what {@code GET
     * /api/admin/draft-cleanup/dry-run} calls. Returns the candidate
     * Order IDs only (never SKU/Supplier/Brand/remark/any business-data
     * field - see this Stage's own logging-safety instruction, §6 of the
     * implementation request).
     */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<Long> findCandidateIds(OffsetDateTime cutoff) {
        List<PortalOrder> base = portalOrderRepository
                .findByStatusAndCurrentRevisionNoIsNullAndSourceOrderIdIsNullAndCreatedAtBefore(
                        PortalOrder.STATUS_DRAFT, cutoff);
        List<Long> candidates = new ArrayList<>();
        for (PortalOrder order : base) {
            Long id = order.getId();
            if (integrationRequestRepository.findFirstByPortalOrderIdOrderByRevisionNoDesc(id).isPresent()) {
                continue;
            }
            if (auditEventRepository.existsByPortalOrderIdAndEventTypeNot(id, AuditEvent.ORDER_DRAFT_CREATED)) {
                continue;
            }
            candidates.add(id);
        }
        return candidates;
    }

    /**
     * Phase 2 (audit §4/§7): the actual safety gate. Takes a pessimistic
     * row lock, re-reads the row's current, true state, and re-checks
     * EVERY candidate condition again (not a subset) before ever calling
     * {@link OrderDraftService#deleteDraft} - which itself re-checks its
     * own (narrower) guard a third time. No delete logic is duplicated
     * here; every actual soft-delete + audit_event write still happens
     * exactly once, inside {@code deleteDraft} itself.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public CleanupOutcome cleanupOneIfStillEligible(Long id, OffsetDateTime cutoff) {
        PortalOrder order = portalOrderRepository.findByIdForUpdate(id).orElse(null);
        if (order == null) {
            // Either genuinely gone, or soft-deleted since candidate
            // selection (findByIdForUpdate is @SQLRestriction-aware) -
            // either way, nothing left to do.
            return CleanupOutcome.SKIPPED_ALREADY_GONE;
        }
        if (!PortalOrder.STATUS_DRAFT.equals(order.getStatus())) {
            return CleanupOutcome.SKIPPED_STATUS_CHANGED;
        }
        if (order.getCurrentRevisionNo() != null) {
            return CleanupOutcome.SKIPPED_HAS_REVISION;
        }
        if (order.getSourceOrderId() != null) {
            return CleanupOutcome.SKIPPED_HAS_REORDER_SOURCE;
        }
        if (!order.getCreatedAt().isBefore(cutoff)) {
            return CleanupOutcome.SKIPPED_TOO_RECENT;
        }
        if (integrationRequestRepository.findFirstByPortalOrderIdOrderByRevisionNoDesc(id).isPresent()) {
            return CleanupOutcome.SKIPPED_HAS_OFFICIAL_PO;
        }
        if (auditEventRepository.existsByPortalOrderIdAndEventTypeNot(id, AuditEvent.ORDER_DRAFT_CREATED)) {
            return CleanupOutcome.SKIPPED_HAS_ACTIVITY;
        }

        try {
            orderDraftService.deleteDraft(id, SYSTEM_PERFORMED_BY, true);
            return CleanupOutcome.DELETED;
        } catch (DraftNotFoundException | DraftDeletionNotAllowedException e) {
            // deleteDraft's own guard disagreed at the very last instant
            // (e.g. a concurrent transaction committed between our lock
            // acquisition above and this call) - treat as skipped, not a
            // failure; the caller's loop still counts this correctly.
            return CleanupOutcome.SKIPPED_STATUS_CHANGED;
        }
    }
}
