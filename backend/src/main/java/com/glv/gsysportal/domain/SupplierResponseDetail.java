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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Technical Design 5.4. {@code orderedQty}/{@code requestedDelivery} are
 * Demo Send-time snapshots, set once by {@code DemoSendService} and never
 * changed afterwards. {@code confirmedQty} is a nullable {@link Integer}
 * (never a primitive {@code int}) - NULL means "not yet answered", 0 means
 * an explicit zero answer. This distinction is the single most important
 * Validation rule in Step 4 (implementation instructions 11章) and must be
 * preserved through every layer (JPA column has no DEFAULT, DTOs use
 * {@code Integer}, JSON `null` is never coerced to 0).
 */
@Entity
@Table(name = "supplier_response_detail")
@Getter
@Setter
@NoArgsConstructor
public class SupplierResponseDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_response_id", nullable = false)
    private SupplierResponse supplierResponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portal_order_detail_id", nullable = false)
    private PortalOrderDetail portalOrderDetail;

    @Column(name = "ordered_qty", nullable = false)
    private int orderedQty;

    /** NULL = not yet answered, 0 = explicit zero answer. Never default this. */
    @Column(name = "confirmed_qty")
    private Integer confirmedQty;

    @Column(name = "requested_delivery")
    private LocalDate requestedDelivery;

    @Column(name = "confirmed_delivery")
    private LocalDate confirmedDelivery;

    @Column(name = "response_note")
    private String responseNote;

    @Column(name = "is_confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
