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
 * Technical Design 5.5. {@code portalOrderDetailId} is NULL for Order-level
 * Attention (e.g. {@link #PARTIAL_CONFIRMATION}) and set for line-level
 * Attention ({@link #QUANTITY_CHANGED}/{@link #DELIVERY_CHANGED}).
 * Duplicate-ACTIVE prevention is DB-enforced (V7 migration's partial unique
 * index, COALESCE-normalized for the NULL detail_id case).
 */
@Entity
@Table(name = "order_attention")
@Getter
@Setter
@NoArgsConstructor
public class OrderAttention {

    public static final String QUANTITY_CHANGED = "QUANTITY_CHANGED";
    public static final String DELIVERY_CHANGED = "DELIVERY_CHANGED";
    /** Order-level (portalOrderDetailId is always null for this type) -
     * implementation instructions 23章: System-generated/transient, may be
     * auto-resolved once every line has an answer (unlike QUANTITY_CHANGED/
     * DELIVERY_CHANGED, which stay ACTIVE until a user acknowledges them). */
    public static final String PARTIAL_CONFIRMATION = "PARTIAL_CONFIRMATION";
    public static final String DATA_OUTDATED = "DATA_OUTDATED";
    public static final String OTHER_ATTENTION = "OTHER_ATTENTION";
    /** Phase 7-C5 8章/9章: raised when a line's explicitly-selected Supply
     * Status is anything other than AVAILABLE - stays ACTIVE until a user
     * acknowledges it, same as QUANTITY_CHANGED/DELIVERY_CHANGED. */
    public static final String SUPPLY_STATUS_CHANGED = "SUPPLY_STATUS_CHANGED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false)
    private Long portalOrderId;

    @Column(name = "portal_order_detail_id")
    private Long portalOrderDetailId;

    @Column(name = "attention_type", nullable = false, length = 30)
    private String attentionType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "acknowledged_by", length = 50)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    private String note;
}
