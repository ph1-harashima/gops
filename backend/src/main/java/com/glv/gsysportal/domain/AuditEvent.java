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
 * Technical Design 5.6. Append-only Audit Trail entry. No repository/service
 * in this codebase issues UPDATE/DELETE against this table (see V3 migration
 * comment). Requirements MD 16章/34章.
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    public static final String ORDER_DRAFT_CREATED = "ORDER_DRAFT_CREATED";
    public static final String ORDER_QTY_CHANGED = "ORDER_QTY_CHANGED";
    public static final String REQUESTED_DELIVERY_CHANGED = "REQUESTED_DELIVERY_CHANGED";
    public static final String REMARK_CHANGED = "REMARK_CHANGED";
    public static final String ORDER_DATE_CHANGED = "ORDER_DATE_CHANGED";
    /** Confirm Order (implementation instructions 8章): written together with
     * {@link #STATUS_CHANGED} in the same transaction - a Status change with
     * no matching Audit row is never allowed to occur. */
    public static final String ORDER_READY = "ORDER_READY";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    /** Return to Draft (implementation instructions 15章): written together
     * with {@link #STATUS_CHANGED}, mirroring the ORDER_READY/STATUS_CHANGED
     * pairing above. */
    public static final String ORDER_RETURNED_TO_DRAFT = "ORDER_RETURNED_TO_DRAFT";

    // --- Step 4 ---
    /** Demo Send (implementation instructions 4章): written once per Demo
     * Send, alongside two STATUS_CHANGED rows (READY_TO_ORDER->SENT and
     * SENT->AWAITING_SUPPLIER) in the same transaction. */
    public static final String DEMO_SENT = "DEMO_SENT";
    /** Supplier Response confirmed (implementation instructions 21章). */
    public static final String SUPPLIER_RESPONSE_RECEIVED = "SUPPLIER_RESPONSE_RECEIVED";
    /** confirmedQty changed on a line (old/new = previous/new confirmedQty,
     * as strings - "null" is a valid old_value meaning "was not yet
     * answered", implementation instructions 12章/16章). */
    public static final String QUANTITY_CHANGED = "QUANTITY_CHANGED";
    /** confirmedDelivery changed on a line (implementation instructions 13章). */
    public static final String DELIVERY_CHANGED = "DELIVERY_CHANGED";
    public static final String ATTENTION_ADDED = "ATTENTION_ADDED";
    public static final String ATTENTION_RESOLVED = "ATTENTION_RESOLVED";

    // --- Phase 7-C1: Role / Approval Foundation ---
    /** OPERATOR (or ADMIN) requested approval: DRAFT -> PENDING_APPROVAL. */
    public static final String SUBMITTED_FOR_APPROVAL = "SUBMITTED_FOR_APPROVAL";
    /** ADMIN approved as-submitted: PENDING_APPROVAL -> APPROVED. */
    public static final String ORDER_APPROVED = "ORDER_APPROVED";
    /** ADMIN edited the Draft while PENDING_APPROVAL and then approved -
     * written INSTEAD of ORDER_APPROVED; the individual field changes are
     * already on the trail as ORDER_QTY_CHANGED etc. rows performed by the
     * approver between submission and this event (7-C1 11章). */
    public static final String APPROVED_WITH_CHANGES = "APPROVED_WITH_CHANGES";
    /** ADMIN returned to OPERATOR: PENDING_APPROVAL -> DRAFT. The mandatory
     * reason is stored in {@link #note} (7-C1 12章). */
    public static final String RETURNED_FOR_CORRECTION = "RETURNED_FOR_CORRECTION";

    // --- Phase 7-C2A: Official PO Integration Foundation ---
    /** ADMIN requested Official PO Integration for an APPROVED Order
     * (Integration Request row created or already existed - written once per
     * Request, not on every idempotent re-call; 7-C2A 19章). Never implies
     * anything was actually sent to Legacy. */
    public static final String OFFICIAL_PO_INTEGRATION_REQUESTED = "OFFICIAL_PO_INTEGRATION_REQUESTED";
    /** Preflight (Legacy READ ONLY) ran for an Integration Request - written
     * every time Preflight runs, including repeat runs on the same Request
     * (7-C2A 19章). {@link #note} holds the overall PASS/WARNING/BLOCKED result. */
    public static final String PRECHECK_COMPLETED = "PRECHECK_COMPLETED";

    // --- Phase 7-C3: Supplier Contact / Mail Template Foundation ---
    /** Mail Preview generated - written every call (mirrors PRECHECK_COMPLETED's
     * own precedent), {@link #note} holds "OK"/"BLOCKED" (7-C3 17章).
     * Supplier Contact / Mail Template CREATED/UPDATED are deliberately NOT
     * modeled as AuditEvent rows - this table is Order-scoped (portal_order_id
     * NOT NULL) and Master changes have no Order to attach to; those Masters'
     * own created_by/updated_by/created_at/updated_at columns serve as their
     * audit trail instead (7-C3 17章's explicit permission to skip if forcing
     * it would be unnatural). */
    public static final String MAIL_PREVIEW_GENERATED = "MAIL_PREVIEW_GENERATED";

    // --- Phase 7-C5: Supplier Response Revision / Agreement Workflow ---
    /** ADMIN Business Action: SUPPLIER_CONFIRMED -> AGREED (7-C5 11章). Written
     * together with STATUS_CHANGED, same pairing convention as every other
     * Status-changing event in this codebase. */
    public static final String SUPPLIER_RESPONSE_AGREED = "SUPPLIER_RESPONSE_AGREED";
    /** "修正版を作成" (7-C5 13章): SUPPLIER_CONFIRMED -> DRAFT, the start of a
     * correction cycle. {@link #note} holds the mandatory reason. Individual
     * field edits during the correction reuse the EXISTING Draft-edit Audit
     * events (ORDER_QTY_CHANGED etc.) verbatim - see V11 migration's comment
     * for why no separate ORDER_REVISION_UPDATED event was added. */
    public static final String ORDER_REVISION_CREATED = "ORDER_REVISION_CREATED";
    /** AGREED -> SUPPLIER_CONFIRMED with a mandatory reason (7-C5 18章).
     * {@code agreed_by}/{@code agreed_at} on the SupplierResponse row are
     * deliberately never cleared by this - see that entity's Javadoc. */
    public static final String AGREEMENT_REOPENED = "AGREEMENT_REOPENED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false)
    private Long portalOrderId;

    @Column(name = "portal_order_detail_id")
    private Long portalOrderDetailId;

    /** Widened 30->50 in V9 (Phase 7-C2A) - OFFICIAL_PO_INTEGRATION_REQUESTED
     * is 33 characters, past the original 30-char limit. */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "field_name", length = 50)
    private String fieldName;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "performed_by", nullable = false, length = 50)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    private String note;

    public AuditEvent(Long portalOrderId, Long portalOrderDetailId, String eventType,
                       String fieldName, String oldValue, String newValue,
                       String performedBy, OffsetDateTime performedAt) {
        this.portalOrderId = portalOrderId;
        this.portalOrderDetailId = portalOrderDetailId;
        this.eventType = eventType;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
    }
}
