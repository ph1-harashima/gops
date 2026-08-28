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
 * <p>This Phase (7-C2A) only ever writes {@link #STATUS_PENDING}. The other
 * values ({@link #STATUS_GENERATED}, {@link #STATUS_SUBMITTED},
 * {@link #STATUS_CONFIRMED}, {@link #STATUS_FAILED}) exist in the State
 * Model for 7-C2B+ and are exercised only by State Transition unit tests in
 * this Phase - no Controller/Service path in this Phase can produce them
 * (7-C2A 16章/17章: "UI/通常APIから偽のCONFIRMEDを作れないこと").
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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Test-only helper exercising the GENERATED transition (7-C2A 16章: no
     * Controller/Service path in this Phase calls this). */
    public void markGenerated(String fileKey, OffsetDateTime now) {
        if (!STATUS_PENDING.equals(status)) {
            throw new IllegalStateException("Cannot mark GENERATED from status " + status);
        }
        this.status = STATUS_GENERATED;
        this.generatedFileKey = fileKey;
        this.generatedAt = now;
        this.updatedAt = now;
    }

    /** Test-only helper exercising the SUBMITTED transition (7-C2B territory). */
    public void markSubmitted(OffsetDateTime now) {
        if (!STATUS_GENERATED.equals(status)) {
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
