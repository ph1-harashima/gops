package com.glv.gsysportal.config;

import com.glv.gsysportal.exception.LegacyUnavailableException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import javax.sql.DataSource;
import java.util.List;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §10): translates {@link DataAccessException} into {@link
 * LegacyUnavailableException} for the Legacy Adapter only, so {@code
 * GlobalExceptionHandler} can return a structured {@code LEGACY_UNAVAILABLE}
 * error instead of letting the failure fall through to a bare 500.
 *
 * <p>Returned in place of a plain {@code NamedParameterJdbcTemplate} by
 * {@link LegacyDataSourceConfig#legacyNamedParameterJdbcTemplate}. Every
 * {@code repository.legacy} class keeps injecting the same {@code
 * NamedParameterJdbcTemplate} type unchanged - Spring supplies this subtype
 * transparently, so zero of the ~15 existing Legacy Read Repository classes
 * needed to change.
 *
 * <p>Only overrides the 3 method overloads actually called anywhere in
 * {@code repository.legacy} (confirmed via a full-package grep before
 * writing this class: every call site uses exactly {@code query(String,
 * SqlParameterSource, RowMapper)}, {@code queryForObject(String,
 * SqlParameterSource, Class)}, {@code queryForList(String,
 * SqlParameterSource, Class)}) - not a general-purpose resilience layer for
 * every possible {@code NamedParameterJdbcTemplate} overload.
 *
 * <p>{@link EmptyResultDataAccessException} from {@code queryForObject} is
 * deliberately NOT translated - a single-row query returning 0 rows is a
 * normal outcome (the query itself succeeded), not Legacy unavailability.
 * Production Readiness Audit §10's explicit requirement: never conflate
 * Failure with Empty Result.
 *
 * <p>No automatic Retry is added here (Production Readiness Audit §16 / this
 * Phase's §10 instruction: whether a READ Query is safe to retry needs
 * per-Source evaluation this Phase does not perform) - a failure is
 * translated and surfaced once, not retried.
 */
public class LegacyFailureTranslatingJdbcTemplate extends NamedParameterJdbcTemplate {

    public LegacyFailureTranslatingJdbcTemplate(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) throws DataAccessException {
        try {
            return super.query(sql, paramSource, rowMapper);
        } catch (DataAccessException e) {
            throw new LegacyUnavailableException(e);
        }
    }

    @Override
    public <T> T queryForObject(String sql, SqlParameterSource paramSource, Class<T> requiredType) throws DataAccessException {
        try {
            return super.queryForObject(sql, paramSource, requiredType);
        } catch (EmptyResultDataAccessException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new LegacyUnavailableException(e);
        }
    }

    @Override
    public <T> List<T> queryForList(String sql, SqlParameterSource paramSource, Class<T> elementType) throws DataAccessException {
        try {
            return super.queryForList(sql, paramSource, elementType);
        } catch (DataAccessException e) {
            throw new LegacyUnavailableException(e);
        }
    }
}
