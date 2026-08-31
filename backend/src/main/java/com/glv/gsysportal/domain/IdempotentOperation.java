package com.glv.gsysportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §11-16) - Technical Idempotency Foundation. See {@code
 * V17__technical_idempotency_foundation.sql} for why this is a new,
 * operation-agnostic table rather than a reuse of {@code
 * official_po_integration_request}.
 *
 * <p>Purely a TECHNICAL record: {@link #status} is one of {@link
 * #STATUS_STARTED}/{@link #STATUS_SUCCEEDED}/{@link #STATUS_FAILED} only -
 * never a Business Workflow State. {@link #operationType} is a caller-chosen
 * technical category (e.g. a future {@code OFFICIAL_PO_HANDOFF}/{@code
 * SUPPLIER_EMAIL_SEND}/{@code EDI_SEND} - none of which are wired to any
 * real External Side Effect yet, this class only provides the Foundation).
 *
 * <p>No real Business Operation reads or writes this table yet this Phase -
 * see {@code IdempotencyServiceTest} for how a future caller would use it.
 */
@Entity
@Table(name = "idempotent_operation")
@Getter
@Setter
@NoArgsConstructor
public class IdempotentOperation {

    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    /** A human/operator-legible description of what this attempt is for
     * (e.g. {@code "portalOrderId=42;revisionNo=1"}) - not itself the
     * dedup key (see {@link #idempotencyKey}), just an audit/troubleshooting
     * aid indexed for lookup. */
    @Column(name = "business_key", nullable = false, length = 200)
    private String businessKey;

    /** The actual duplicate-prevention key, unique together with {@link
     * #operationType} (DB constraint - see migration). Caller-supplied;
     * often equal to {@link #businessKey} but kept as a separate field so a
     * caller can use a different granularity (e.g. one key per Revision
     * attempt vs. one key per Order). */
    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String status = STATUS_STARTED;

    @Column(nullable = false)
    private int attempt = 1;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    /** Optimistic Locking - protects the retry-after-FAILED path (a
     * concurrent lost-update on the same existing row) the same way the DB
     * UNIQUE constraint protects the initial concurrent-INSERT race. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public IdempotentOperation(String operationType, String businessKey, String idempotencyKey, OffsetDateTime now) {
        this.operationType = operationType;
        this.businessKey = businessKey;
        this.idempotencyKey = idempotencyKey;
        this.status = STATUS_STARTED;
        this.attempt = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
