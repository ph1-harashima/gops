package com.glv.gsysportal.dto.response;

import java.util.List;

/**
 * Master Maintenance Hub: the Supplier Settings "Overview" tab. Same
 * aggregation sources as {@link SupplierMasterSummaryResponse}, plus the
 * Brand list itself (derived the same way Dashboard's own Brand breakdown
 * already is - from Order Candidate/Order data, never a new Brand Master).
 */
public record SupplierMasterDetailResponse(
        String supplierCode,
        String supplierName,
        List<BrandRef> brands,
        String regionClassification,
        boolean contactConfigured,
        int activeContactCount,
        String channel,
        String officialPoShortCode
) {
    public record BrandRef(String brandCode, String brandName) {
    }
}
