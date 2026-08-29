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
 * Phase 7-C6: Excel / Legacy Concurrency Control Foundation
 * (docs/excel-legacy-concurrency-control.md 4章). An append-only "Portal
 * confirmed G-SYS looked like THIS at this point in time" snapshot - never
 * UPDATEd or DELETEd once created (same convention as {@link AuditEvent}/
 * {@link PortalOrderRevision}). Written only by an explicit ADMIN Business
 * Action ("G-SYS現在状態を基準として記録" - {@code LegacyPoConcurrencyService.captureBaseline}),
 * never automatically.
 *
 * <p>{@code revisionNo} is the SAME concept as
 * {@link OfficialPoIntegrationRequest#getRevisionNo()}/
 * {@link PortalOrderRevision#getRevisionNo()} - not a separate numbering
 * scheme (7-C6 16章 "非常に重要": a Revision 1 Baseline must never be reused
 * for Revision 2's Compare). {@code officialPoNo} is snapshotted here too
 * (not just looked up live via {@link PortalOrder#getOfficialPoNo()}) so a
 * historical Baseline row remains self-describing even if the Order's own
 * {@code officialPoNo} were ever to change later.
 *
 * <p>{@code snapshotJson} holds the full Canonical Snapshot
 * ({@code LegacyPoSnapshot}, header + Canonically-ordered lines) as JSON -
 * this is what {@code LegacyPoDiffEngine} actually diffs against on Compare.
 * {@code fingerprint} (SHA-256 of that same JSON) is a fast identity check
 * only - Concurrency Detection, not a Security boundary (7-C6 3章).
 */
@Entity
@Table(name = "legacy_po_baseline")
@Getter
@Setter
@NoArgsConstructor
public class LegacyPoBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false)
    private Long portalOrderId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "official_po_no", nullable = false, length = 30)
    private String officialPoNo;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "snapshot_json", nullable = false)
    private String snapshotJson;

    @Column(name = "captured_by", nullable = false, length = 50)
    private String capturedBy;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;
}
