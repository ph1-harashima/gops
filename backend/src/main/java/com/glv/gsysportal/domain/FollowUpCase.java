package com.glv.gsysportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Phase 7-C7A 9章: a Portal-only concept - no equivalent exists in Legacy (0章's
 * audit confirmed no Back Order / 督促 / 再発注 functionality exists anywhere
 * in {@code phasep-gulliver}). Created only by an explicit human action
 * ("問い合わせ対象にする", 12章) - never auto-generated from a Fulfillment
 * calculation.
 */
@Entity
@Table(name = "follow_up_case")
@Getter
@Setter
@NoArgsConstructor
public class FollowUpCase {

    public static final String STATUS_OPEN = "OPEN";
    /** Set automatically the first time a non-BLOCKED Mail Preview is
     * generated for this Case (7-C7A 13章/10章 - "実際に送っていないのに
     * INQUIRY_SENTを設定できる設計は禁止" led to reusing the Preview action
     * itself as the meaningful transition, rather than adding a separate
     * manual "mark as prepared" button that could drift out of sync with
     * whether a Preview was ever actually generated). */
    public static final String STATUS_INQUIRY_PREPARED = "INQUIRY_PREPARED";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String REASON_DELIVERY_OVERDUE = "DELIVERY_OVERDUE";
    public static final String REASON_PARTIAL_DELIVERY = "PARTIAL_DELIVERY";
    public static final String REASON_NO_ARRIVAL = "NO_ARRIVAL";
    public static final String REASON_QUANTITY_DIFFERENCE = "QUANTITY_DIFFERENCE";
    public static final String REASON_OTHER = "OTHER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false)
    private Long portalOrderId;

    @Column(name = "order_revision_id")
    private Long orderRevisionId;

    /** Snapshotted at creation time - never re-read from portal_order at
     * render time (7-C7A migration comment). */
    @Column(name = "official_po_no", length = 30)
    private String officialPoNo;

    /** NULL = Order-level Case, not about one specific line. */
    @Column(name = "sku_code", length = 30)
    private String skuCode;

    @Column(nullable = false, length = 20)
    private String status = STATUS_OPEN;

    @Column(nullable = false, length = 30)
    private String reason;

    private String note;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "closed_by", length = 50)
    private String closedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
}
