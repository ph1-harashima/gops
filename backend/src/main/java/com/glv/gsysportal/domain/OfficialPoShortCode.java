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
 * Confirmed Business Rule BR-08 (docs/gulliver-20260917-confirmed-business-rules.md):
 * the 3-character Supplier/Brand abbreviation Gulliver decides and registers
 * (conceptually, in G-SYS Master). Legacy `ms_comm` (MS_SUPPL/MS_BRAND) has
 * no such column today (READ ONLY re-confirmed) - this is a Portal-only
 * Master an ADMIN populates with Gulliver's own decided values, mirroring
 * {@link SupplierRegionClassification}'s own precedent for a Business
 * Concept Legacy has not yet caught up with. G-OPS itself never generates
 * or infers a short code - {@link com.glv.gsysportal.service.OfficialPoNumberGenerator}
 * only ever reads whichever value is registered here.
 */
@Entity
@Table(name = "official_po_short_code")
@Getter
@Setter
@NoArgsConstructor
public class OfficialPoShortCode {

    public static final String TYPE_SUPPLIER = "SUPPLIER";
    public static final String TYPE_BRAND = "BRAND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_type", nullable = false, length = 10)
    private String codeType;

    /** The existing Legacy Supplier/Brand Code (e.g. {@code SUP_ALPHA}) this
     * 3-character abbreviation belongs to - not the abbreviation itself. */
    @Column(name = "business_code", nullable = false, length = 10)
    private String businessCode;

    @Column(name = "short_code", nullable = false, length = 3)
    private String shortCode;

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
