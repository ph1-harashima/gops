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
 * Phase 7-C2A: Official PO Integration Foundation
 * (docs/official-po-integration-detailed-design.md 13章's
 * {@code official_po_integration_request} design, {@code officialPoNo}
 * gating from 5.2章/7章). Prototype PostgreSQL only.
 *
 * <p>One row per (portalOrderId, revisionNo) - the Idempotency key (7-C2A
 * 4章). {@code revisionNo} is always 1 this Phase (no Revision Workflow
 * exists yet - 7-C5 will introduce it; 7-C2A 5章).
 *
 * <p>Status is the Integration axis, entirely separate from
 * {@link PortalOrder#getStatus()} (Business Workflow Status) - 7-C2-Design
 * 18章 / 7-C2A 2章. "NOT_REQUESTED" is never stored: it is the absence of a
 * row for that (orderId, revisionNo), not a persisted value.
 *
 * <p>7-C2A only ever wrote {@link #STATUS_PENDING} (16章/17章: "UI/通常APIから
 * 偽のCONFIRMEDを作れないこと"). Phase 9-A (Production PO Workflow) added the
 * first real caller of {@link #STATUS_GENERATED} (Excel generation), Phase
 * 9-B added {@link #STATUS_SUBMITTED}/{@link #STATUS_FAILED} (Import Folder
 * hand-off). {@link #STATUS_CONFIRMED} still has no real caller (Phase
 * 9-C's G-SYS Import Confirmation) - only State Transition unit tests
 * exercise it directly.
 */
@Entity
@Table(name = "official_po_integration_request")
@Getter
@Setter
@NoArgsConstructor
public class OfficialPoIntegrationRequest {

    /** Integration Request created (+ Preflight run); Excel not yet generated. */
    public static final String STATUS_PENDING = "PENDING";
    /** Excel Generator Foundation produced a byte[] (7-C2A 8章). Only ever set
     * via {@link #markGenerated}, exercised by tests this Phase. */
    public static final String STATUS_GENERATED = "GENERATED";
    /** Handed off to the Legacy Import Folder (7-C2B). */
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    /** Legacy TR_PO/TR_PO_DTL confirmed to match (7-C2B+, via READ ONLY polling). */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** Submission or confirmation failed/timed out (7-C2B+). Deliberately
     * distinct from a Preflight BLOCKED result (7-C2A 18章) - Preflight never
     * reached Legacy WRITE at all, so it is not a Legacy Integration failure. */
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false)
    private Long portalOrderId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo = 1;

    /** G-SYS Official PO No. actually used for this Request/Revision. NULL
     * throughout 7-C2A (7章's Gate) - only a future Phase, once the numbering
     * rule is decided, assigns this. */
    @Column(name = "official_po_no", length = 30)
    private String officialPoNo;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** Reference/key to a generated Excel artifact (7-C2-Design 15章's
     * S3-or-local storage role). Unused this Phase (no Controller path ever
     * generates one) - present for Foundation completeness. */
    @Column(name = "generated_file_key", length = 255)
    private String generatedFileKey;

    @Column(name = "requested_by", nullable = false, length = 50)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** Overall verdict of the most recent Preflight run - PASS/WARNING/BLOCKED
     * (7-C2A 12章). Null until Preflight has run at least once. */
    @Column(name = "preflight_result", length = 20)
    private String preflightResult;

    /** JSON array of {@code {code, severity, message, skuCode}} issues from
     * the most recent Preflight run (7-C2A 12章). Plain TEXT + manual Jackson
     * (de)serialization in the Service layer, matching this codebase's
     * existing preference for free-text columns (e.g. AuditEvent.note) over
     * introducing a JSONB Hibernate type mapping for one nested list. */
    @Column(name = "preflight_issues_json")
    private String preflightIssuesJson;

    @Column(name = "preflight_at")
    private OffsetDateTime preflightAt;

    /** Phase 7-C6 12章/13章: distinguishes "this Official PO No. is expected
     * to be a brand-new G-SYS PO" ({@link #INTENT_NEW}) from "expected to
     * already exist and is being updated" ({@link #INTENT_UPDATE}) - so a
     * PO_NOT_FOUND Concurrency result is never automatically treated as an
     * error for a NEW-intent Order. Null until an ADMIN explicitly sets it
     * (7-C6 13章 "自動推定は慎重にする" - the numbering rule itself is still
     * unresolved CUSTOMER REVIEW, so this is never inferred). */
    public static final String INTENT_NEW = "NEW";
    public static final String INTENT_UPDATE = "UPDATE";

    @Column(name = "integration_intent", length = 10)
    private String integrationIntent;

    // --- Phase 9-A: Excel-contract fields with no other home in the domain
    // (docs/official-po-integration-detailed-design.md §3.2) - staff-entered
    // alongside the PO No. confirm, integration-specific rather than
    // Draft/Order-editing concepts. All nullable; Legacy's own Required/
    // Optional split (Delivery Week/Date required, Ship Via/Term/Payment Term
    // optional) is enforced only at Excel-generate time, not at column level. ---
    @Column(name = "delivery_week", length = 5)
    private String deliveryWeek;

    @Column(name = "delivery_date", length = 50)
    private String deliveryDate;

    @Column(name = "ship_via", length = 100)
    private String shipVia;

    @Column(name = "ship_term", length = 100)
    private String shipTerm;

    @Column(name = "payment_term", length = 100)
    private String paymentTerm;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // --- Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md
    // 7章): PDF artifact, independent of the Integration Status state
    // machine above (PDF is never handed off to the Import Folder/Legacy -
    // it never drives PENDING/GENERATED/SUBMITTED/CONFIRMED/FAILED). ---
    @Column(name = "pdf_file_key", length = 255)
    private String pdfFileKey;

    @Column(name = "pdf_generated_at")
    private OffsetDateTime pdfGeneratedAt;

    // --- Gap Analysis C-2/C-4 (docs/gulliver-20260917-phase1-gap-analysis.md
    // 7章): Document lifecycle, entirely separate from the Integration
    // Status axis above. ---
    public static final String LIFECYCLE_ACTIVE = "ACTIVE";
    public static final String LIFECYCLE_SUPERSEDED = "SUPERSEDED";
    public static final String LIFECYCLE_CANCELLED = "CANCELLED";

    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private String lifecycleStatus = LIFECYCLE_ACTIVE;

    @Column(name = "lifecycle_reason")
    private String lifecycleReason;

    @Column(name = "lifecycle_changed_by", length = 50)
    private String lifecycleChangedBy;

    @Column(name = "lifecycle_changed_at")
    private OffsetDateTime lifecycleChangedAt;

    /** C-2: this Revision's Request has been replaced by a newer one
     * (Reissue). Reachable only from ACTIVE - a SUPERSEDED or CANCELLED
     * Request is a terminal historical record and is never re-superseded. */
    public void markSuperseded(String reason, String performedBy, OffsetDateTime now) {
        if (!LIFECYCLE_ACTIVE.equals(lifecycleStatus)) {
            throw new IllegalStateException("Cannot supersede from lifecycle status " + lifecycleStatus);
        }
        this.lifecycleStatus = LIFECYCLE_SUPERSEDED;
        this.lifecycleReason = reason;
        this.lifecycleChangedBy = performedBy;
        this.lifecycleChangedAt = now;
        this.updatedAt = now;
    }

    /** C-4: ADMIN explicitly cancels this Document (reason required at the
     * Service layer). Reachable only from ACTIVE - matches markSuperseded's
     * own "terminal states never transition again" rule. */
    public void markCancelled(String reason, String performedBy, OffsetDateTime now) {
        if (!LIFECYCLE_ACTIVE.equals(lifecycleStatus)) {
            throw new IllegalStateException("Cannot cancel from lifecycle status " + lifecycleStatus);
        }
        this.lifecycleStatus = LIFECYCLE_CANCELLED;
        this.lifecycleReason = reason;
        this.lifecycleChangedBy = performedBy;
        this.lifecycleChangedAt = now;
        this.updatedAt = now;
    }

    /** GENERATED transition. Called for real by
     * {@code OfficialPoIntegrationService#generateExcel} since Phase 9-A -
     * only ever from PENDING (a re-generate on an already-GENERATED+
     * Request is handled as an idempotent no-op one layer up, in the
     * Service, never by relaxing this guard). */
    public void markGenerated(String fileKey, OffsetDateTime now) {
        if (!STATUS_PENDING.equals(status)) {
            throw new IllegalStateException("Cannot mark GENERATED from status " + status);
        }
        this.status = STATUS_GENERATED;
        this.generatedFileKey = fileKey;
        this.generatedAt = now;
        this.updatedAt = now;
    }

    /** SUBMITTED transition. Called for real by
     * {@code OfficialPoIntegrationService#placeToImportFolder} since Phase
     * 9-B - reachable from GENERATED (first placement attempt) OR FAILED
     * (retrying a placement that previously failed; the Excel itself is not
     * regenerated, so this Request never needs to revisit GENERATED just to
     * retry - Production PO Workflow §7's "Retry時の重複処理防止"). */
    public void markSubmitted(OffsetDateTime now) {
        if (!STATUS_GENERATED.equals(status) && !STATUS_FAILED.equals(status)) {
            throw new IllegalStateException("Cannot mark SUBMITTED from status " + status);
        }
        this.status = STATUS_SUBMITTED;
        this.submittedAt = now;
        this.updatedAt = now;
    }

    /** Test-only helper exercising the CONFIRMED transition (7-C2B+ territory). */
    public void markConfirmed(OffsetDateTime now) {
        if (!STATUS_SUBMITTED.equals(status)) {
            throw new IllegalStateException("Cannot mark CONFIRMED from status " + status);
        }
        this.status = STATUS_CONFIRMED;
        this.confirmedAt = now;
        this.updatedAt = now;
    }

    /** Test-only helper exercising the FAILED transition (7-C2B+ territory).
     * Reachable from GENERATED or SUBMITTED (submission or confirmation can
     * each fail) - never from PENDING (Preflight BLOCKED is not this). */
    public void markFailed(String errorCode, String errorMessage, OffsetDateTime now) {
        if (!STATUS_GENERATED.equals(status) && !STATUS_SUBMITTED.equals(status)) {
            throw new IllegalStateException("Cannot mark FAILED from status " + status);
        }
        this.status = STATUS_FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.failedAt = now;
        this.retryCount++;
        this.updatedAt = now;
    }
}
