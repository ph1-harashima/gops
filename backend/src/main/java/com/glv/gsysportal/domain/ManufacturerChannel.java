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
 * Phase 9-D: Manufacturer Communication Channel Master (Portal-only - Legacy
 * has no such Master at all, same situation as {@link SupplierContact}).
 * Records which Channel (EMAIL/EDI) a Supplier[+Brand] actually uses for
 * placing Orders - the ~90%/10% split from the Working Assumption
 * (production-oriented PO workflow §2). {@code supplierCode}/{@code brandCode}
 * reference Legacy Master data (validated Legacy READ ONLY at write time,
 * {@code ManufacturerChannelService}) but are never FK-constrained against
 * Legacy.
 *
 * <p>Resolution priority (supplier+brand exact match, then supplier-only,
 * no further fallback) lives in {@code ManufacturerChannelResolutionService},
 * mirroring {@code SupplierContactResolutionService}'s own precedent.
 */
@Entity
@Table(name = "manufacturer_channel")
@Getter
@Setter
@NoArgsConstructor
public class ManufacturerChannel {

    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_EDI = "EDI";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_code", nullable = false, length = 10)
    private String supplierCode;

    /** Null = applies to every Brand under this Supplier (the "supplier-only"
     * Resolution tier), same convention as {@link SupplierContact#getBrandCode()}. */
    @Column(name = "brand_code", length = 10)
    private String brandCode;

    @Column(nullable = false, length = 10)
    private String channel;

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
