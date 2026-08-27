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
 * Technical Design 5.2. Order Draft line - Snapshot fields are set once at
 * Create Draft time from a Backend-side Legacy re-fetch and never trusted
 * from the Frontend (implementation instructions 4章).
 */
@Entity
@Table(name = "portal_order_detail")
@Getter
@Setter
@NoArgsConstructor
public class PortalOrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portal_order_id", nullable = false)
    private PortalOrder portalOrder;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(nullable = false, length = 30)
    private String sku;

    @Column(name = "item_name_snapshot", nullable = false, length = 200)
    private String itemNameSnapshot;

    /** Immutable after Create Draft - calc4 at Draft-creation time. Never edited via API. */
    @Column(name = "recommended_qty", nullable = false)
    private int recommendedQty;

    @Column(name = "order_qty", nullable = false)
    private int orderQty;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "current_stock_snapshot")
    private Integer currentStockSnapshot;

    @Column(name = "safety_stock_snapshot")
    private Integer safetyStockSnapshot;

    @Column(name = "open_po_snapshot")
    private Integer openPoSnapshot;

    @Column(name = "recent_sales_snapshot")
    private Integer recentSalesSnapshot;

    @Column(name = "lead_time_snapshot", length = 10)
    private String leadTimeSnapshot;

    @Column(name = "item_status_snapshot", length = 30)
    private String itemStatusSnapshot;

    @Column(name = "data_source", nullable = false, length = 20)
    private String dataSource = PortalOrder.DATA_SOURCE_DEMO_LEGACY;

    @Column(name = "is_removed", nullable = false)
    private boolean removed;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
