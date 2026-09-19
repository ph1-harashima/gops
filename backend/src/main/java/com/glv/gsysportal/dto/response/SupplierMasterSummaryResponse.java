package com.glv.gsysportal.dto.response;

/**
 * Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
 * one row of the Supplier List. Pure aggregation over already-existing data -
 * supplierCode/supplierName come from Legacy {@code ms_comm} (READ ONLY,
 * same Source of Truth {@code OfficialPoPreflightReadRepository.supplierExists}
 * already validates against); brandCount/contactConfigured/channel/
 * officialPoShortCode are derived from the existing Order Candidate/Order/
 * Supplier Contact/Manufacturer Channel/Region Classification/Official PO
 * Short Code data - no new Portal Master table, no duplicated Business
 * Logic.
 */
public record SupplierMasterSummaryResponse(
        String supplierCode,
        String supplierName,
        /** "OVERSEAS" | "DOMESTIC" | "MIXED" | null (no active row for any Brand of this Supplier). */
        String regionClassification,
        int brandCount,
        boolean contactConfigured,
        /** "EMAIL" | "EDI" | "MIXED" | null. */
        String channel,
        /** This Supplier's own Official PO Short Code (codeType=SUPPLIER), or null if not yet configured. */
        String officialPoShortCode
) {
}
