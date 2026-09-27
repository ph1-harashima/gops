package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.PortalOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Phase 8-J 3章/4章: {@link JpaSpecificationExecutor} added so Order History
 * List can push its Filters + Pageable to the DB (see
 * OrderHistoryService#list) instead of the previous findAll()+Java Stream
 * filter. No new query method was needed for this - Specification composes
 * the existing filter predicates dynamically.
 */
public interface PortalOrderRepository extends JpaRepository<PortalOrder, Long>, JpaSpecificationExecutor<PortalOrder> {
    boolean existsByDraftNo(String draftNo);

    /**
     * Draft Lifecycle Cleanup Audit §6/§10 candidate pre-filter: the 3
     * cheap, single-column conditions (status/currentRevisionNo/
     * sourceOrderId/createdAt). {@code deleted_at IS NULL} is NOT written
     * here explicitly - this is a standard JPA-entity-backed derived query,
     * so {@link PortalOrder}'s own class-level {@code @SQLRestriction
     * ("deleted_at IS NULL")} applies automatically (unlike a direct {@code
     * findById}, which is documented elsewhere in this codebase to bypass
     * it). The two remaining conditions (no Official PO Integration
     * Request, no audit_event beyond ORDER_DRAFT_CREATED) require joins
     * this derived-query form cannot express cleanly - {@link
     * com.glv.gsysportal.service.DraftCleanupService#findCandidateIds}
     * applies those in Java over this method's own (typically small,
     * low-hundreds-row) result set, the same "derived query then Java
     * filter" shape already established by {@code SupplierMasterService}
     * for a similarly small table.
     */
    List<PortalOrder> findByStatusAndCurrentRevisionNoIsNullAndSourceOrderIdIsNullAndCreatedAtBefore(
            String status, OffsetDateTime cutoff);

    /**
     * Draft Lifecycle Cleanup Audit §4/§7 Defense in Depth: a JPQL-backed
     * (not the plain generated {@code findById}) by-ID lookup, taken under
     * a pessimistic row lock. Being JPQL, {@code @SQLRestriction} applies
     * automatically (a soft-deleted row simply is not returned, exactly
     * like {@link #findActiveById}) - callers never need a separate {@code
     * deletedAt != null} check on top of this. The lock itself closes the
     * "candidate selected, then user saves/submits, then cleanup deletes"
     * race window (§7): a concurrent transaction that also touches this
     * row (Save/Submit-for-Approval/Delete, all of which load the row via
     * Hibernate and therefore participate in Postgres's own row-level
     * locking) blocks until this transaction commits or rolls back, so the
     * re-verification that follows this call is guaranteed to see the
     * row's true, final state - never a stale snapshot.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PortalOrder o WHERE o.id = :id")
    Optional<PortalOrder> findByIdForUpdate(@Param("id") Long id);

    /**
     * Draft Lifecycle Cleanup Audit §5: the "findActiveById" shared pattern
     * requested by that audit, to close the pre-existing gap where several
     * Services looked up a Draft by the plain generated {@code findById}
     * (which bypasses {@code @SQLRestriction}) with no explicit {@code
     * deletedAt} check, so a soft-deleted DRAFT-status Order could still be
     * reached by ID. A JPQL {@code @Query} (unlike {@code findById})
     * respects the entity's own {@code @SQLRestriction} automatically, so
     * this single method gives every caller the same "not found" behavior
     * for a soft-deleted row that a genuinely-nonexistent ID already gets -
     * no per-call-site {@code if (deletedAt != null) throw ...} boilerplate
     * needed. Existing, already-tested call sites in {@code
     * OrderDraftService} (which predate this audit and already carry their
     * own explicit checks) are intentionally left unchanged.
     */
    @Query("SELECT o FROM PortalOrder o WHERE o.id = :id")
    Optional<PortalOrder> findActiveById(@Param("id") Long id);
}
