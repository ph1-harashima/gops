package com.glv.gsysportal.repository.legacy.row;

/** Mirrors Legacy TR_PO (Technical Design terms: PO_NO/STATUS/PO_TYPE/SUPPLIER_CD/BRAND_CD). */
public record LegacyPoHeaderRow(String poNo, String status, String poType, String supplierCd, String brandCd) {
}
