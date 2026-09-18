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
 * Phase 9-E: Real Email Send. One row per (portalOrderId, revisionNo) - same
 * Idempotency-key shape as {@link OfficialPoIntegrationRequest} (V9).
 * Created on the first Send attempt (never on Preview - {@code MailPreviewService}
 * never touches this table).
 */
@Entity
@Table(name = "order_email")
@Getter
@Setter
@NoArgsConstructor
public class OrderEmail {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    public static final String ATTACHMENT_TYPE_OFFICIAL_PO_EXCEL = "OFFICIAL_PO_EXCEL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false)
    private Long portalOrderId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo = 1;

    @Column(name = "from_address", length = 200)
    private String fromAddress;

    /** Comma-separated - display/audit only, never re-parsed for business
     * logic (this Entity's own Javadoc in the migration explains why a
     * plain TEXT column, not JSONB, was chosen). */
    @Column(name = "to_addresses")
    private String toAddresses;

    @Column(name = "cc_addresses")
    private String ccAddresses;

    @Column(columnDefinition = "TEXT")
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "attachment_type", nullable = false, length = 50)
    private String attachmentType = ATTACHMENT_TYPE_OFFICIAL_PO_EXCEL;

    @Column(name = "attachment_file_key", length = 255)
    private String attachmentFileKey;

    @Column(nullable = false, length = 20)
    private String status = STATUS_DRAFT;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "sent_by", length = 50)
    private String sentBy;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // --- Gap Analysis C-5 (docs/gulliver-20260917-phase1-gap-analysis.md
    // 10章): Email Recipient Override. toAddresses/ccAddresses above stay
    // "what was actually sent" (Phase 9-E's original meaning, unchanged) -
    // these record "what the Master (Supplier Contact Resolution) actually
    // resolved to", so an Override is always distinguishable in the record
    // even when Send later happens without one. ---
    @Column(name = "master_to_addresses")
    private String masterToAddresses;

    @Column(name = "master_cc_addresses")
    private String masterCcAddresses;

    @Column(name = "recipient_override_used", nullable = false)
    private boolean recipientOverrideUsed;

    public void markSent(OffsetDateTime now, String performedBy) {
        this.status = STATUS_SENT;
        this.sentAt = now;
        this.sentBy = performedBy;
        this.errorCode = null;
        this.errorMessage = null;
        this.updatedAt = now;
    }

    public void markFailed(String errorCode, String errorMessage, OffsetDateTime now) {
        this.status = STATUS_FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryCount++;
        this.updatedAt = now;
    }
}
