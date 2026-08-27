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
 * Technical Design 5.6. Append-only Audit Trail entry. No repository/service
 * in this codebase issues UPDATE/DELETE against this table (see V3 migration
 * comment). Requirements MD 16章/34章.
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    public static final String ORDER_DRAFT_CREATED = "ORDER_DRAFT_CREATED";
    public static final String ORDER_QTY_CHANGED = "ORDER_QTY_CHANGED";
    public static final String REQUESTED_DELIVERY_CHANGED = "REQUESTED_DELIVERY_CHANGED";
    public static final String REMARK_CHANGED = "REMARK_CHANGED";
    public static final String ORDER_DATE_CHANGED = "ORDER_DATE_CHANGED";
    /** Confirm Order (implementation instructions 8章): written together with
     * {@link #STATUS_CHANGED} in the same transaction - a Status change with
     * no matching Audit row is never allowed to occur. */
    public static final String ORDER_READY = "ORDER_READY";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    /** Return to Draft (implementation instructions 15章): written together
     * with {@link #STATUS_CHANGED}, mirroring the ORDER_READY/STATUS_CHANGED
     * pairing above. */
    public static final String ORDER_RETURNED_TO_DRAFT = "ORDER_RETURNED_TO_DRAFT";

    // --- Step 4 ---
    /** Demo Send (implementation instructions 4章): written once per Demo
     * Send, alongside two STATUS_CHANGED rows (READY_TO_ORDER->SENT and
     * SENT->AWAITING_SUPPLIER) in the same transaction. */
    public static final String DEMO_SENT = "DEMO_SENT";
    /** Supplier Response confirmed (implementation instructions 21章). */
    public static final String SUPPLIER_RESPONSE_RECEIVED = "SUPPLIER_RESPONSE_RECEIVED";
    /** confirmedQty changed on a line (old/new = previous/new confirmedQty,
     * as strings - "null" is a valid old_value meaning "was not yet
     * answered", implementation instructions 12章/16章). */
    public static final String QUANTITY_CHANGED = "QUANTITY_CHANGED";
    /** confirmedDelivery changed on a line (implementation instructions 13章). */
    public static final String DELIVERY_CHANGED = "DELIVERY_CHANGED";
    public static final String ATTENTION_ADDED = "ATTENTION_ADDED";
    public static final String ATTENTION_RESOLVED = "ATTENTION_RESOLVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_order_id", nullable = false)
    private Long portalOrderId;

    @Column(name = "portal_order_detail_id")
    private Long portalOrderDetailId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "field_name", length = 50)
    private String fieldName;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "performed_by", nullable = false, length = 50)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    private String note;

    public AuditEvent(Long portalOrderId, Long portalOrderDetailId, String eventType,
                       String fieldName, String oldValue, String newValue,
                       String performedBy, OffsetDateTime performedAt) {
        this.portalOrderId = portalOrderId;
        this.portalOrderDetailId = portalOrderDetailId;
        this.eventType = eventType;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
    }
}
