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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Technical Design 5.3. "1 Order = 1 current-state header" - complete change
 * history is reconstructed from audit_event, never from a separate
 * version table (implementation instructions 8章).
 */
@Entity
@Table(name = "supplier_response")
@Getter
@Setter
@NoArgsConstructor
public class SupplierResponse {

    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false, unique = true)
    private Long portalOrderId;

    @Column(name = "response_date")
    private LocalDate responseDate;

    @Column(name = "response_note")
    private String responseNote;

    @Column(name = "response_status", nullable = false, length = 20)
    private String responseStatus = STATUS_PARTIAL;

    @Column(name = "received_by", length = 50)
    private String receivedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "supplierResponse", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    private List<SupplierResponseDetail> details = new ArrayList<>();
}
