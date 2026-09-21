package com.glv.gsysportal.repository.legacy.row;

/**
 * Stage 5H Systematic Performance Remediation (RC-H): one (Supplier, Brand)
 * pair that has at least one non-deleted Item whose most recent PO was
 * placed with that Supplier - see SupplierBrandAssociationQuery.sql's own
 * header comment. Brand display name is deliberately not carried here -
 * resolved by the caller via the existing bulk brand-name lookup instead.
 */
public record LegacySupplierBrandAssociationRow(String supplierCode, String brandCode) {
}
