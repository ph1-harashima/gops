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

/**
 * Stage 5K, V34 migration: the Brand Aggregate - one row per (refresh,
 * Brand) per SUCCEEDED {@link DashboardRefreshRun}. {@code brandCode} is
 * Legacy-sourced (never a Portal FK - no Brand master table exists in
 * Portal, same precedent {@link SkuExpectedRestock#getSkuCode()} already
 * set for Legacy-sourced codes).
 */
@Entity
@Table(name = "dashboard_brand_legacy_aggregate")
@Getter
@Setter
@NoArgsConstructor
public class DashboardBrandLegacyAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refresh_run_id", nullable = false)
    private Long refreshRunId;

    @Column(name = "brand_code", nullable = false, length = 10)
    private String brandCode;

    /** Captured at Refresh time - see V34 migration's own comment on this
     * column for why (keeps the Dashboard/Brand List request path
     * entirely Legacy-free, not just calc4-free). */
    @Column(name = "brand_name", length = 200)
    private String brandName;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "out_of_stock_count", nullable = false)
    private int outOfStockCount;

    @Column(name = "long_term_out_of_stock_count", nullable = false)
    private int longTermOutOfStockCount;

    public DashboardBrandLegacyAggregate(Long refreshRunId, String brandCode, String brandName, int candidateCount,
                                          int outOfStockCount, int longTermOutOfStockCount) {
        this.refreshRunId = refreshRunId;
        this.brandCode = brandCode;
        this.brandName = brandName;
        this.candidateCount = candidateCount;
        this.outOfStockCount = outOfStockCount;
        this.longTermOutOfStockCount = longTermOutOfStockCount;
    }
}
