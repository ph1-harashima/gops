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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Technical Design 5.3. "1 Order+Revision = 1 current-state header" - complete
 * change history is reconstructed from audit_event, never from a separate
 * version table (implementation instructions 8章). Phase 7-C5 5章 relaxed the
 * original "1 Order = 1 header" 1:1 constraint to "1 (Order, Revision) = 1
 * header": each {@link PortalOrderRevision} Send gets its own new
 * SupplierResponse row, and past rows (for a superseded Revision) are never
 * modified again - they are the READ ONLY response history.
 */
@Entity
@Table(name = "supplier_response")
@Getter
@Setter
@NoArgsConstructor
public class SupplierResponse {

    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false)
    private Long portalOrderId;

    /** Phase 7-C5 5章: which {@link PortalOrderRevision} this Response answers.
     * Combined with {@code portalOrderId}, unique per row (V11 migration) -
     * one Response per Revision, replacing the old one-per-Order constraint. */
    @Column(name = "order_revision_id", nullable = false)
    private Long orderRevisionId;

    @Column(name = "response_date")
    private LocalDate responseDate;

    @Column(name = "response_note")
    private String responseNote;

    @Column(name = "response_status", nullable = false, length = 20)
    private String responseStatus = STATUS_PARTIAL;

    @Column(name = "received_by", length = 50)
    private String receivedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Phase 7-C5 11章: set only by the Agreement Business Action. Never
     * cleared by Reopen (7-C5 18章 "履歴を消さない") - {@link #reopenedAt}
     * records the most recent Reopen alongside it instead. */
    @Column(name = "agreed_by", length = 50)
    private String agreedBy;

    @Column(name = "agreed_at")
    private OffsetDateTime agreedAt;

    @Column(name = "reopened_by", length = 50)
    private String reopenedBy;

    @Column(name = "reopened_at")
    private OffsetDateTime reopenedAt;

    @Column(name = "reopen_reason")
    private String reopenReason;

    @OneToMany(mappedBy = "supplierResponse", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    private List<SupplierResponseDetail> details = new ArrayList<>();
}
