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
 * Phase 7-C3: Mail Template Master (Portal-only). Only
 * {@link #TEMPLATE_TYPE_PURCHASE_ORDER} has real Resolution logic this
 * Phase ({@code MailTemplateResolutionService}) - the other 3 candidate
 * types are allowed to be stored (forward-compatibility, 7-C3 6章) but
 * nothing resolves against them yet.
 */
@Entity
@Table(name = "mail_template")
@Getter
@Setter
@NoArgsConstructor
public class MailTemplate {

    public static final String TEMPLATE_TYPE_PURCHASE_ORDER = "PURCHASE_ORDER";
    public static final String TEMPLATE_TYPE_PURCHASE_ORDER_REVISION = "PURCHASE_ORDER_REVISION";
    public static final String TEMPLATE_TYPE_FOLLOW_UP = "FOLLOW_UP";
    public static final String TEMPLATE_TYPE_CANCELLATION = "CANCELLATION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "template_type", nullable = false, length = 30)
    private String templateType;

    /** Null = applies to every Supplier (the "language default" Resolution
     * tier - only meaningful when {@link #brandCode} is also null). */
    @Column(name = "supplier_code", length = 10)
    private String supplierCode;

    /** Null = applies to every Brand under {@link #supplierCode} (or, when
     * supplierCode is also null, to every Supplier). */
    @Column(name = "brand_code", length = 10)
    private String brandCode;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(name = "subject_template", nullable = false, length = 500)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;

    /** Free-text this Phase - "OFFICIAL_PO_EXCEL" is the only meaningful
     * value, used only for Preview's Attachment Foundation metadata (7-C3
     * 16章). No real File is ever attached/generated from this field. */
    @Column(name = "attachment_type", length = 50)
    private String attachmentType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
