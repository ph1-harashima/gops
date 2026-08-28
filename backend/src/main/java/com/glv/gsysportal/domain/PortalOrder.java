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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Technical Design 5.1. Order Draft header. Prototype PostgreSQL only.
 */
@Entity
@Table(name = "portal_order")
@Getter
@Setter
@NoArgsConstructor
public class PortalOrder {

    public static final String STATUS_DRAFT = "DRAFT";
    /** Phase 7-C1: OPERATOR has requested ADMIN approval - the Draft is
     * locked against OPERATOR edits (only ADMIN may still modify it, for the
     * edit-and-approve path) until it is approved or returned. */
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    /** Phase 7-C1: ADMIN approved; the prototype PO No. is assigned at this
     * transition (Target Design 5.1 - replaces the pre-7-C1 READY_TO_ORDER,
     * which migration V8 mapped onto this value). The READY_TO_ORDER string
     * still appears in historical audit_event rows and keeps its i18n label
     * on the Frontend for that reason. */
    public static final String STATUS_APPROVED = "APPROVED";
    /** Intermediate Status within the single Demo Send transaction
     * (implementation instructions 3章/4章) - persisted for Audit/History
     * accuracy, but the user always sees {@link #STATUS_AWAITING_SUPPLIER}
     * as the resting state; Demo Send never leaves an Order sitting in SENT. */
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_AWAITING_SUPPLIER = "AWAITING_SUPPLIER";
    public static final String STATUS_SUPPLIER_CONFIRMED = "SUPPLIER_CONFIRMED";

    /** Matches com.glv.gsysportal.service.OrderCandidateService.DATA_SOURCE_CODE. */
    public static final String DATA_SOURCE_DEMO_LEGACY = "DEMO_LEGACY";
    public static final String DATA_SOURCE_LEGACY = "LEGACY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_no", nullable = false, unique = true, length = 30)
    private String draftNo;

    /** Portal-internal display number, assigned at {@code approve()}. Format
     * {@code PO-DEMO-yyyyMMdd-####} (see PrototypePoNoGenerator) - deliberately
     * NOT G-SYS's Official PO No. and never sent to Legacy in any form
     * (Phase 7-C2-Design 5.1章 / docs/official-po-integration-detailed-design.md). */
    @Column(name = "prototype_po_no", length = 30)
    private String prototypePoNo;

    /** G-SYS Official PO No. (Legacy {@code TR_PO.PO_NO}). Phase 7-C2A: always
     * NULL - this Phase never auto-assigns it (7-C2-Design 5.2章/7-C2A 7章 -
     * the real numbering rule is unresolved CUSTOMER REVIEW). The column
     * exists now so the three PO-number concepts (draftNo / prototypePoNo /
     * officialPoNo) are distinguishable in Source/DTO/DB from the start,
     * rather than retrofitted later. Only a future Phase, once the numbering
     * rule is decided, may ever write a non-null value here. */
    @Column(name = "official_po_no", length = 30)
    private String officialPoNo;

    @Column(name = "supplier_code", nullable = false, length = 10)
    private String supplierCode;

    @Column(name = "supplier_name_snapshot", nullable = false, length = 200)
    private String supplierNameSnapshot;

    @Column(name = "brand_code", nullable = false, length = 10)
    private String brandCode;

    @Column(name = "brand_name_snapshot", nullable = false, length = 200)
    private String brandNameSnapshot;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "requested_delivery")
    private LocalDate requestedDelivery;

    @Column(length = 10)
    private String currency;

    @Column(nullable = false, length = 30)
    private String status = STATUS_DRAFT;

    private String remark;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "data_source", nullable = false, length = 20)
    private String dataSource = DATA_SOURCE_DEMO_LEGACY;

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

    @OneToMany(mappedBy = "portalOrder", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("lineNo ASC")
    private List<PortalOrderDetail> details = new ArrayList<>();
}
