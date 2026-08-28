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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 7-C5: Supplier Response Revision / Agreement Workflow
 * (docs/supplier-response-revision-workflow.md 2章). An immutable, append-only
 * snapshot of "what was actually sent to the Supplier" for one Send of one
 * Order - never UPDATEd or DELETEd once created, exactly like
 * {@link AuditEvent}. Written exactly once, by
 * {@code OrderStatusTransitionService.demoSend}, at the same instant
 * {@link SupplierResponse}/{@link SupplierResponseDetail} are created for
 * that same Send - the two are always created together in one transaction.
 *
 * <p>{@code revisionNo} is Order-scoped (1, 2, 3, ...) and is the SAME
 * concept as {@link OfficialPoIntegrationRequest#getRevisionNo()} - not a
 * separate numbering scheme (7-C5 3章 "非常に重要").
 */
@Entity
@Table(name = "portal_order_revision")
@Getter
@Setter
@NoArgsConstructor
public class PortalOrderRevision {

    /** Revision 1 - always created at the first ever Demo Send, never carries a reason. */
    public static final String TYPE_INITIAL = "INITIAL";
    /** Revision 2+ - created at a Demo Send that follows a "修正版を作成"
     * correction cycle (7-C5 13章); always carries a reason. */
    public static final String TYPE_CORRECTION = "CORRECTION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false)
    private Long portalOrderId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "revision_type", nullable = false, length = 20)
    private String revisionType;

    /** Mandatory for {@link #TYPE_CORRECTION}, always null for {@link #TYPE_INITIAL}
     * (DB CHECK constraint enforces this, 7-C5 14章's "変更理由を必須推奨"). */
    private String reason;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    private List<PortalOrderRevisionDetail> details = new ArrayList<>();
}
