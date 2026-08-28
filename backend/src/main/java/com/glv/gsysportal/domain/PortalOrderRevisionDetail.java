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
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Phase 7-C5: one immutable per-line snapshot row within a
 * {@link PortalOrderRevision}. {@code lineNo}/{@code currency} are
 * deliberately NOT duplicated here - see V11 migration's comment - only
 * fields that can genuinely vary per line within one Order are snapshotted,
 * matching {@link SupplierResponseDetail}'s own precedent.
 */
@Entity
@Table(name = "portal_order_revision_detail")
@Getter
@Setter
@NoArgsConstructor
public class PortalOrderRevisionDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id", nullable = false)
    private PortalOrderRevision revision;

    @Column(name = "sku_code", nullable = false, length = 30)
    private String skuCode;

    @Column(name = "item_name_snapshot", nullable = false, length = 200)
    private String itemNameSnapshot;

    @Column(name = "recommended_qty", nullable = false)
    private int recommendedQty;

    @Column(name = "ordered_qty", nullable = false)
    private int orderedQty;

    @Column(name = "requested_delivery")
    private LocalDate requestedDelivery;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
