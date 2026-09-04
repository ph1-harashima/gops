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
    /** Phase 7-H (EDI発注Workflow Foundation): the EDI-path counterpart to
     * {@link #DEMO_SENT} - written once per {@code
     * OrderStatusTransitionService.recordEdiSend}, alongside the SAME two
     * STATUS_CHANGED rows (APPROVED->SENT and SENT->AWAITING_SUPPLIER) in
     * the same transaction. Records "the Order was communicated to the
     * Supplier via their own EDI system" as a fact for Audit/History - no
     * real EDI file/API/connection is ever involved. */
    public static final String EDI_SEND_RECORDED = "EDI_SEND_RECORDED";
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

    // --- Phase 7-C7A: Fulfillment / Follow-up Foundation ---
    /** "問い合わせ対象にする" (7-C7A 12章) - never auto-generated from a
     * Fulfillment calculation, only an explicit human action. */
    public static final String FOLLOW_UP_CREATED = "FOLLOW_UP_CREATED";
    /** Note edited on an existing Follow-up Case. */
    public static final String FOLLOW_UP_UPDATED = "FOLLOW_UP_UPDATED";
    public static final String FOLLOW_UP_CLOSED = "FOLLOW_UP_CLOSED";
    /** A Reorder Draft was created from a Follow-up Case (7-C7A 16章). */
    public static final String REORDER_CREATED = "REORDER_CREATED";

    // --- Phase 7-C6: Excel / Legacy Concurrency Control Foundation ---
    /** ADMIN explicit Business Action ("G-SYS現在状態を基準として記録",
     * 7-C6 9章) - written every Capture call, including re-captures of an
     * already-Baselined Revision. */
    public static final String LEGACY_PO_BASELINE_CAPTURED = "LEGACY_PO_BASELINE_CAPTURED";
    /** Written ONLY when Compare finds the Legacy PO has actually diverged
     * from its Baseline (7-C6 18章) - never for an UNCHANGED result, to avoid
     * generating a row on every ordinary Order Detail page view. */
    public static final String LEGACY_PO_CHANGE_DETECTED = "LEGACY_PO_CHANGE_DETECTED";

    // --- Phase 8-B: Price Change Foundation ---
    /** Written once per Change Set creation (target-price-change-workflow.md
     * Section 10's minimum list). */
    public static final String PRICE_CHANGE_SET_CREATED = "PRICE_CHANGE_SET_CREATED";
    /** A SKU was added to a Change Set (Baseline Snapshot captured in the
     * same transaction). */
    public static final String PRICE_CHANGE_DETAIL_ADDED = "PRICE_CHANGE_DETAIL_ADDED";
    /** {@code proposedPrcSellWTax} changed on a Detail row - old/new values
     * as strings, "null" is a valid old_value meaning "not yet entered"
     * (same idiom as {@link #QUANTITY_CHANGED}). */
    public static final String PRICE_CHANGE_PROPOSED_PRICE_CHANGED = "PRICE_CHANGE_PROPOSED_PRICE_CHANGED";
    public static final String PRICE_CHANGE_DETAIL_REMOVED = "PRICE_CHANGE_DETAIL_REMOVED";
    public static final String PRICE_CHANGE_NOTE_CHANGED = "PRICE_CHANGE_NOTE_CHANGED";

    // --- Phase 9-A: Official PO Number / Excel Generation (Production PO Workflow) ---
    /** ADMIN confirmed/changed the G-SYS Official PO No. for the current
     * Integration Request - written only when the number itself actually
     * changes (old/new value), not on every idempotent re-confirm with the
     * same value. */
    public static final String OFFICIAL_PO_NUMBER_CONFIRMED = "OFFICIAL_PO_NUMBER_CONFIRMED";
    /** Official PO Excel generated from Portal Order data - written every
     * generate call, mirroring PRECHECK_COMPLETED's own precedent. */
    public static final String OFFICIAL_PO_EXCEL_GENERATED = "OFFICIAL_PO_EXCEL_GENERATED";

    // --- Phase 9-B: Import Folder Integration (Production PO Workflow) ---
    /** Excel successfully placed into the (local-only, this environment)
     * Import Folder - written on every successful placement, including a
     * successful retry after a prior FAILED attempt. */
    public static final String OFFICIAL_PO_FILE_PLACED = "OFFICIAL_PO_FILE_PLACED";
    /** Placement failed (Adapter threw) - {@link #note} holds the error
     * detail. Retry (re-calling the same Business Action) is always
     * possible afterward. */
    public static final String OFFICIAL_PO_FILE_PLACEMENT_FAILED = "OFFICIAL_PO_FILE_PLACEMENT_FAILED";

    // --- Phase 9-C: G-SYS Import Confirmation (Production PO Workflow) ---
    /** TR_PO/TR_PO_DTL confirmed (via Legacy READ ONLY) to match Portal's
     * current Order lines - written only on the call that actually
     * transitions the Integration Request to CONFIRMED, never on a
     * NOT_YET_IMPORTED/MISMATCH check (those produce no state change and no
     * Audit row - re-checking is expected to happen repeatedly before it
     * eventually matches). */
    public static final String OFFICIAL_PO_IMPORT_CONFIRMED = "OFFICIAL_PO_IMPORT_CONFIRMED";

    // --- Phase 9-D: Email / EDI branching (Production PO Workflow) ---
    /** "EDI入力完了" - written once per completion (ADMIN/OPERATOR marks that
     * the Supplier's own EDI system now has this Order's input). */
    public static final String EDI_INPUT_COMPLETED = "EDI_INPUT_COMPLETED";

    // --- Phase 9-E: Real Email Send (Production PO Workflow) ---
    /** Email successfully delivered (via whichever EmailSenderPort Adapter
     * is active) - written on every successful Send, including a successful
     * retry after a prior FAILED attempt. */
    public static final String EMAIL_SENT = "EMAIL_SENT";
    /** Send failed (Adapter threw) - {@link #note} holds the error detail.
     * Retry (re-calling the same Business Action) is always possible
     * afterward. */
    public static final String EMAIL_SEND_FAILED = "EMAIL_SEND_FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false)
    private Long portalOrderId;

    @Column(name = "portal_order_detail_id")
    private Long portalOrderDetailId;

    /** Phase 8-B: the sibling aggregate-root reference to
     * {@link #portalOrderId} for Price Change events - exactly one of the two
     * is ever set (DB-enforced by {@code ck_audit_event_aggregate_root}, V16
     * migration). Kept on this SAME shared table rather than a new
     * {@code price_change_audit_event} table per
     * customer-review-decision-package.md 16.3章's "共通基盤は技術的に妥当な
     * 形で共通化する" - the append-only Audit Trail behavior is identical for
     * both aggregate roots and has no reason to fork. */
    @Column(name = "price_change_set_id")
    private Long priceChangeSetId;

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

    /** Price Change counterpart to the Order-scoped constructor above - a
     * static factory rather than a second overload because
     * {@code (Long priceChangeSetId, ...)} would otherwise be ambiguous with
     * {@code (Long portalOrderId, ...)} at the same erased signature. */
    public static AuditEvent forPriceChangeSet(Long priceChangeSetId, String eventType,
                                                String fieldName, String oldValue, String newValue,
                                                String performedBy, OffsetDateTime performedAt) {
        AuditEvent event = new AuditEvent();
        event.priceChangeSetId = priceChangeSetId;
        event.eventType = eventType;
        event.fieldName = fieldName;
        event.oldValue = oldValue;
        event.newValue = newValue;
        event.performedBy = performedBy;
        event.performedAt = performedAt;
        return event;
    }
}
