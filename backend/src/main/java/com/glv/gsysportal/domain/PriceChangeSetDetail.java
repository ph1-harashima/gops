package com.glv.gsysportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Price Change Request / Change Set line (Phase 8-B). Baseline Snapshot
 * fields (target-price-change-workflow.md 7章/13章) are set exactly once, at
 * "add this SKU to the Change Set" time, from a Backend-side Legacy
 * READ ONLY re-fetch ({@code LegacyPriceReadRepository}) - never trusted
 * from the Frontend, same convention as {@link PortalOrderDetail}'s own
 * Snapshot fields. They exist purely for Concurrency detection (13章), NOT
 * as a Price History record - Price History (target-price-change-workflow.md
 * 14章) is a separate, still-CUSTOMER-REVIEW-gated concept (PC-5) that this
 * Phase does not implement.
 */
@Entity
@Table(name = "price_change_set_detail")
@Getter
@Setter
@NoArgsConstructor
public class PriceChangeSetDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_change_set_id", nullable = false)
    private PriceChangeSet priceChangeSet;

    @Column(name = "item_cd", nullable = false, length = 30)
    private String itemCd;

    @Column(name = "item_name_snapshot", length = 200)
    private String itemNameSnapshot;

    @Column(name = "brand_code_snapshot", length = 10)
    private String brandCodeSnapshot;

    @Column(name = "item_grp_cd_snapshot", length = 30)
    private String itemGrpCdSnapshot;

    /** Baseline (Legacy Current Price at the moment this Detail was added -
     * target-price-change-workflow.md 8章A/13章). Compared against a fresh
     * {@code LegacyPriceReadRepository} read by Concurrency Check; never
     * updated afterward by anything other than an explicit "re-baseline"
     * action (not implemented in Phase 8-B - Foundation only provides the
     * comparison, never a Policy for what to do about a mismatch, Section 8). */
    @Column(name = "baseline_prc_sell_w_tax", precision = 10, scale = 2)
    private BigDecimal baselinePrcSellWTax;

    @Column(name = "baseline_cost_this_month_avg", precision = 10, scale = 2)
    private BigDecimal baselineCostThisMonthAvg;

    @Column(name = "baseline_free_ship_flg")
    private Boolean baselineFreeShipFlg;

    @Column(name = "baseline_ship_fee", precision = 10, scale = 2)
    private BigDecimal baselineShipFee;

    @Column(name = "baseline_captured_at", nullable = false)
    private OffsetDateTime baselineCapturedAt;

    /** Proposed new Selling Price (tax-included, same unit as
     * {@code baseline_prc_sell_w_tax}/Legacy's {@code PRC_SELL_W_TAX}). Null
     * while the Detail has been added but no value entered yet. */
    @Column(name = "proposed_prc_sell_w_tax", precision = 10, scale = 2)
    private BigDecimal proposedPrcSellWTax;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
