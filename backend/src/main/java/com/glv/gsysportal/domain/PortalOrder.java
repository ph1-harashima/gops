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
    /** Phase 7-C5: explicit ADMIN Business Action only - Supplier Response
     * being CONFIRMED never implies AGREED (7-C5 10章/19章 "Supplier Response
     * 確定 ≠ AGREED" must always hold). */
    public static final String STATUS_AGREED = "AGREED";

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

    /** Phase 7-H (EDI発注Workflow監査): deliberately separate from {@code
     * status} - "the Order/PO is confirmed and was communicated to the
     * Supplier" (the Status transition to AWAITING_SUPPLIER) and "which
     * Channel that communication used" are two independent facts, per this
     * Phase's own design principle. NULL until the first Send (either path
     * below); never read by any Approval/Supplier Response/Fulfillment/
     * Follow-up logic - Supplier Response, Revision, and Agreement all key
     * off {@code status} alone, unaffected by which value this holds
     * (confirmed via Source audit before adding this field - SupplierResponseService
     * never reads this column). Two values so far, both written only by
     * {@link com.glv.gsysportal.service.OrderStatusTransitionService}:
     * {@link #CHANNEL_EMAIL} (existing Demo Send path, unchanged behavior) and
     * {@link #CHANNEL_EDI} (new Phase 7-H Foundation path - records that a
     * Supplier was ordered from over their own EDI system rather than
     * Email, WITHOUT implementing any real EDI integration - see that
     * Service's Javadoc). Real EDI file formats/APIs/auth are explicitly
     * out of this Phase's scope and remain CUSTOMER REVIEW. */
    @Column(name = "communication_channel", length = 20)
    private String communicationChannel;

    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_EDI = "EDI";

    /** Phase 7-C5 3章: the Revision most recently sent to the Supplier - the
     * SAME concept as {@link OfficialPoIntegrationRequest#getRevisionNo()},
     * not a separate numbering scheme. NULL until the first Demo Send
     * (mirrors the {@code prototypePoNo}/{@code officialPoNo} "unassigned
     * until milestone" idiom above). Set only by
     * {@code OrderStatusTransitionService.demoSend} - never incremented by
     * "修正版を作成" itself, since a Revision only crystallizes at Send time
     * (docs/supplier-response-revision-workflow.md 2章). */
    @Column(name = "current_revision_no")
    private Integer currentRevisionNo;

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

    /** Phase 7-C7A 15章/16章: Reorder Foundation - set only on a Reorder
     * Draft (created via the ordinary {@code OrderDraftService.createDraft}
     * path, then stamped with these 3 Reference columns; the create path
     * itself is untouched). NULL for every ordinary Order. */
    @Column(name = "source_order_id")
    private Long sourceOrderId;

    @Column(name = "source_follow_up_case_id")
    private Long sourceFollowUpCaseId;

    @Column(name = "reorder_reason")
    private String reorderReason;

    @OneToMany(mappedBy = "portalOrder", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("lineNo ASC")
    private List<PortalOrderDetail> details = new ArrayList<>();
}
