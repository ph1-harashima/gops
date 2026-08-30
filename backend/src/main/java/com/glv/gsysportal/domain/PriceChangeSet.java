package com.glv.gsysportal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Price Change Request / Change Set header (Phase 8-B, Price Change
 * Foundation). target-price-change-workflow.md 6章's minimal State Skeleton
 * ONLY - PENDING_APPROVAL/APPROVED/SCHEDULED are deliberately not modeled
 * (CUSTOMER REVIEW-gated, see that Document's 17章 PC-2/PC-3/PC-6). A new,
 * independent aggregate root - no {@code portal_order_id} anywhere on this
 * entity or {@link PriceChangeSetDetail} (confirmed no Business/Technical
 * Dependency on Ordering, customer-review-decision-package.md 16.2章).
 */
@Entity
@Table(name = "price_change_set")
@Getter
@Setter
@NoArgsConstructor
public class PriceChangeSet {

    /** Being edited - Details may still be added/removed/re-priced. */
    public static final String STATUS_DRAFT = "DRAFT";
    /** Terminal-pending: submitted for G-SYS reflection (target-price-change-workflow.md
     * 12章 Integration Foundation) but no real Excel Artifact/Import is ever
     * produced by Phase 8-B (Section 1 "実G-SYS反映"/"実Excel Import" are
     * both forbidden this Phase) - this Status exists as a structural
     * placeholder only; nothing in this codebase transitions a Change Set
     * INTO this state yet. */
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    /** Confirmed reflected into G-SYS (Target Design 8章C). Not reachable in
     * Phase 8-B (no real Integration exists yet) - present for the State enum
     * to be complete/self-documenting, matching how {@code PortalOrder}
     * pre-declares Statuses ahead of the Phase that first reaches them. */
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String status = STATUS_DRAFT;

    private String note;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private int version;

    @OneToMany(mappedBy = "priceChangeSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PriceChangeSetDetail> details = new ArrayList<>();
}
