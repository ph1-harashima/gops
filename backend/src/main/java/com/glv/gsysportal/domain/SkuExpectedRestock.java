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
 * Post-Freeze Business Refinement (docs/gops-20260917-business-requirements-re-audit.md
 * §5-11): a Portal-only, SKU-keyed Manual Input record for "when will this
 * SKU be back in stock" (Type C - Supplier Expected Restock Date), used
 * only when Legacy has no Expected Arrival Date to show (i.e. no PO
 * currently in flight for this SKU - exactly the 長期欠品 case per
 * {@code DashboardService.isLongTermOutOfStock}). Never consulted or
 * overridden when Legacy's own ETA/ETA_WH (Type A, {@code
 * ArrivalSummaryResponse}) is available - Manual Input never overwrites the
 * Legacy-derived value (re-audit doc §8).
 *
 * <p>One row per SKU, updated in place - unlike every other Portal Master
 * (Supplier Contact/Manufacturer Channel/Region Classification/Short Code),
 * there is no soft-delete/is_active concept here: "nothing recorded yet" is
 * already fully expressed by the row simply not existing.
 *
 * <p>{@link #isUnknown} and {@link #expectedRestockDate} are mutually
 * exclusive (DB-enforced, V32 migration) - affirmatively recording "asked,
 * no answer yet" is a distinct state from "not yet looked into" (no row) or
 * "a real date is known" (date set, isUnknown false).
 *
 * <p>Deliberately independent of Supplier/Brand/Official PO Short Code Data
 * Model - keyed on SKU alone, per the explicit instruction not to touch
 * those structures this round pending UAT real-data confirmation.
 *
 * <p>Post-Freeze Business Refinement 2
 * (docs/gops-manufacturer-stockout-information-management.md): broadened
 * from "just a restock date" into "Manufacturer Stockout Information" -
 * {@link #stockoutStatus}/{@link #shortageQty}/
 * {@link #informationReceivedDate}/{@link #contactMethod} added (V33) to
 * this SAME table/row, since the record is still one-per-SKU
 * updated-in-place. Every save also appends a
 * {@link SkuManufacturerStockoutHistory} snapshot - this entity itself
 * only ever holds the CURRENT state.
 */
@Entity
@Table(name = "sku_expected_restock")
@Getter
@Setter
@NoArgsConstructor
public class SkuExpectedRestock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_code", nullable = false, length = 50)
    private String skuCode;

    @Column(name = "expected_restock_date")
    private LocalDate expectedRestockDate;

    @Column(name = "is_unknown", nullable = false)
    private boolean unknown = false;

    @Column(length = 500)
    private String memo;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** STOCKOUT / LONG_TERM_STOCKOUT / RESOLVED, or null when this record
     * predates Implementation 2 / was never classified (backward
     * compatible with Implementation 1 rows that only ever set a date). */
    @Column(name = "stockout_status", length = 30)
    private String stockoutStatus;

    /** Nullable and never defaulted to 0 - "confirmed short by some amount"
     * and "heard it's stockout with no quantity discussed" are different
     * states (requirements doc §7). */
    @Column(name = "shortage_qty")
    private Integer shortageQty;

    /** When the Manufacturer actually communicated this, distinct from
     * {@link #updatedAt} (when G-OPS was updated) - requirements doc §9. */
    @Column(name = "information_received_date")
    private LocalDate informationReceivedDate;

    /** PHONE / EMAIL / ORDER_RESPONSE / OTHER, or null. */
    @Column(name = "contact_method", length = 20)
    private String contactMethod;
}
