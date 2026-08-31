package com.glv.gsysportal.exception;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §10): thrown in place of the raw Spring {@code DataAccessException} for any
 * failure reaching the Legacy Adapter's actually-used JDBC methods (see
 * {@code com.glv.gsysportal.config.LegacyFailureTranslatingJdbcTemplate}) -
 * connection refused, timeout, driver error, etc.
 *
 * Deliberately NOT a {@code DataAccessException} subtype: {@link
 * com.glv.gsysportal.exception.GlobalExceptionHandler} distinguishes "Legacy
 * unavailable" from "Portal DB unavailable" purely by exception TYPE (this
 * class vs. any surviving {@code DataAccessException}) rather than
 * inspecting messages/causes, which would be fragile across JDBC drivers.
 *
 * Never thrown for a genuine empty result (0 rows) - see
 * LegacyFailureTranslatingJdbcTemplate's explicit exclusion of
 * {@code EmptyResultDataAccessException}. Failure and Empty Result must stay
 * distinguishable (Production Readiness Audit §10's explicit requirement).
 */
public class LegacyUnavailableException extends RuntimeException {
    public LegacyUnavailableException(Throwable cause) {
        super("Legacy G-SYS Adapter query failed", cause);
    }
}
