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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Post-Freeze Business Refinement 2
 * (docs/gops-manufacturer-stockout-information-management.md §11) - a
 * Business-facing timeline of {@link SkuExpectedRestock} changes, separate
 * from {@code audit_event}'s Technical Audit (still written on every
 * change too, via the existing {@code SKU_EXPECTED_RESTOCK_CHANGED} event
 * type). Append-only: one row per change, snapshotting the FULL resulting
 * state (not a diff), so a screen can render
 * "09/20 長期欠品/未定/電話 -> 10/05 長期欠品/11-15/メール -> ..." directly.
 * Never updated or deleted, including across a RESOLVED transition.
 */
@Entity
@Table(name = "sku_manufacturer_stockout_history")
@Getter
@Setter
@NoArgsConstructor
public class SkuManufacturerStockoutHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_code", nullable = false, length = 50)
    private String skuCode;

    @Column(name = "stockout_status", length = 30)
    private String stockoutStatus;

    @Column(name = "expected_restock_date")
    private LocalDate expectedRestockDate;

    @Column(name = "is_unknown", nullable = false)
    private boolean unknown = false;

    @Column(name = "shortage_qty")
    private Integer shortageQty;

    @Column(name = "information_received_date")
    private LocalDate informationReceivedDate;

    @Column(name = "contact_method", length = 20)
    private String contactMethod;

    @Column(length = 500)
    private String memo;

    @Column(name = "recorded_by", nullable = false, length = 50)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;
}
