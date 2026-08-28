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
 * Phase 7-C3: Supplier Contact Master (Portal-only - Legacy has no Supplier
 * email/contact/CC Master, docs/official-po-integration-detailed-design.md's
 * sibling design 1章). {@code supplierCode}/{@code brandCode} reference
 * Legacy Master data (validated Legacy READ ONLY at write time,
 * {@code SupplierContactService}) but are never FK-constrained against
 * Legacy - Legacy DB is a different database entirely (Technical Design 4.1).
 *
 * <p>Resolution priority (supplier+brand exact match, then supplier-only,
 * no further fallback) lives in {@code SupplierContactResolutionService},
 * not here.
 */
@Entity
@Table(name = "supplier_contact")
@Getter
@Setter
@NoArgsConstructor
public class SupplierContact {

    public static final String CONTACT_TYPE_TO = "TO";
    public static final String CONTACT_TYPE_CC = "CC";

    public static final String LANGUAGE_JA = "ja";
    public static final String LANGUAGE_EN = "en";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_code", nullable = false, length = 10)
    private String supplierCode;

    /** Null = applies to every Brand under this Supplier (the "supplier-only"
     * Resolution tier). */
    @Column(name = "brand_code", length = 10)
    private String brandCode;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(name = "contact_type", nullable = false, length = 10)
    private String contactType;

    @Column(nullable = false, length = 10)
    private String language;

    /** CUSTOMER REVIEW territory (7-C3 5章/18章) - not used by Resolution
     * this Phase (Order carries no region/procurementType field yet), kept
     * for forward-compatibility only. */
    @Column(length = 50)
    private String region;

    @Column(name = "procurement_type", length = 50)
    private String procurementType;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
