package com.glv.gsysportal.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Technical Design 5.1. Order Draft header. Prototype PostgreSQL only.
 */
@Entity
@Table(name = "portal_order")
@Getter
@Setter
@NoArgsConstructor
public class PortalOrder {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_READY_TO_ORDER = "READY_TO_ORDER";

    /** Matches com.glv.gsysportal.service.OrderCandidateService.DATA_SOURCE_CODE. */
    public static final String DATA_SOURCE_DEMO_LEGACY = "DEMO_LEGACY";
    public static final String DATA_SOURCE_LEGACY = "LEGACY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_no", nullable = false, unique = true, length = 30)
    private String draftNo;

    @Column(name = "prototype_po_no", length = 30)
    private String prototypePoNo;

    @Column(name = "supplier_code", nullable = false, length = 10)
    private String supplierCode;

    @Column(name = "supplier_name_snapshot", nullable = false, length = 200)
    private String supplierNameSnapshot;

    @Column(name = "brand_code", nullable = false, length = 10)
    private String brandCode;

    @Column(name = "brand_name_snapshot", nullable = false, length = 200)
    private String brandNameSnapshot;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "requested_delivery")
    private LocalDate requestedDelivery;

    @Column(length = 10)
    private String currency;

    @Column(nullable = false, length = 30)
    private String status = STATUS_DRAFT;

    private String remark;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "data_source", nullable = false, length = 20)
    private String dataSource = DATA_SOURCE_DEMO_LEGACY;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private int version;

    @OneToMany(mappedBy = "portalOrder", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("lineNo ASC")
    private List<PortalOrderDetail> details = new ArrayList<>();
}
