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
 * Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章):
 * Domestic/Overseas Foundation - a Portal-only Master (Legacy has no such
 * classification for Supplier at all, re-confirmed READ ONLY this Phase -
 * same situation {@link ManufacturerChannel}'s own Javadoc documents for
 * Communication Channel). Records which region classification (DOMESTIC/
 * OVERSEAS) a Supplier[+Brand] belongs to, as a Working Assumption ADMIN
 * sets manually - NOT derived from any Legacy field.
 *
 * <p><b>This classification is never consulted by
 * {@code RecommendedQtyCalculator}/{@code OrderQuantityCalculator}</b> -
 * it exists purely so the Frontend can display "国内"/"海外" on an Order,
 * as a foundation a future Phase could branch a Strategy on <i>after</i>
 * the actual Business Rule difference is confirmed with the customer (Gap
 * Analysis 6章's explicit "国内用/海外用の新しい計算式を勝手に作らない"
 * instruction). Resolution priority (brand-specific over supplier-only, no
 * further fallback) lives in {@code SupplierRegionClassificationResolutionService},
 * mirroring {@code ManufacturerChannelResolutionService}'s own precedent.
 */
@Entity
@Table(name = "supplier_region_classification")
@Getter
@Setter
@NoArgsConstructor
public class SupplierRegionClassification {

    public static final String DOMESTIC = "DOMESTIC";
    public static final String OVERSEAS = "OVERSEAS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_code", nullable = false, length = 10)
    private String supplierCode;

    /** Null = applies to every Brand under this Supplier (the "supplier-only"
     * Resolution tier), same convention as {@link ManufacturerChannel#getBrandCode()}. */
    @Column(name = "brand_code", length = 10)
    private String brandCode;

    @Column(name = "region_classification", nullable = false, length = 10)
    private String regionClassification;

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
