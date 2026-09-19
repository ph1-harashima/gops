package com.glv.gsysportal.repository.legacy.row;

/**
 * Plain projection row for {@code ms_comm CATE_ID='MS_SUPPL'} - the Legacy
 * Supplier Master. Deliberately NOT a JPA @Entity (Technical Design 4.2:
 * Legacy is read via JdbcTemplate + RowMapper, never entity-mapped).
 */
public record LegacySupplierRow(String code, String name) {
}
