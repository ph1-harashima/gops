package com.glv.gsysportal.config;

import com.glv.gsysportal.exception.LegacyUnavailableException;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §10/§23): confirms Legacy failure and Legacy empty-result stay
 * distinguishable, and that failure is translated to {@link
 * LegacyUnavailableException} rather than surfacing a raw {@code
 * DataAccessException}/500.
 */
@SpringBootTest
@ActiveProfiles("test")
class LegacyFailureTranslatingJdbcTemplateTest {

    /** Real Legacy bean from the Application Context - genuinely the
     * LegacyFailureTranslatingJdbcTemplate instance every repository.legacy
     * class uses (LegacyDataSourceConfig.legacyNamedParameterJdbcTemplate). */
    @Autowired
    private LegacyStockReadRepository legacyStockReadRepository;

    @Autowired
    private NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate;

    private HikariDataSource unreachableDataSource;

    @AfterEach
    void closeUnreachableDataSource() {
        if (unreachableDataSource != null) {
            unreachableDataSource.close();
        }
    }

    /** A genuine connection failure (unreachable host) - not a mock, an
     * actual HikariDataSource pointed at a port nothing listens on, with a
     * short timeout so the test fails fast rather than hanging. */
    @Test
    void connectionFailureIsTranslatedToLegacyUnavailableException() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://127.0.0.1:1/legacy_demo?connectTimeout=500");
        config.setUsername("unused");
        config.setPassword("unused");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1000);
        config.setInitializationFailTimeout(-1); // don't fail pool creation itself, fail on first use
        unreachableDataSource = new HikariDataSource(config);

        LegacyFailureTranslatingJdbcTemplate template = new LegacyFailureTranslatingJdbcTemplate(unreachableDataSource);

        assertThrows(LegacyUnavailableException.class, () ->
                template.queryForList("SELECT 1", new MapSqlParameterSource(), Integer.class));
    }

    /** A genuinely empty result (0 rows) from the real, reachable Legacy
     * Demo MySQL must NOT be reported as LegacyUnavailableException -
     * Failure and Empty Result stay distinguishable (§10's explicit
     * requirement). findBySkus() on a definitely-nonexistent SKU returns an
     * empty List (not an exception) - the underlying query() path never
     * throws EmptyResultDataAccessException in the first place, which is
     * exactly the desired behavior for a List-returning query. */
    @Test
    void genuineEmptyResultFromRealLegacyIsNotTreatedAsFailure() {
        var rows = legacyStockReadRepository.findBySkus(java.util.Set.of("NO-SUCH-SKU-8L-TEST"));
        assertTrue(rows.isEmpty(), "0 matching rows is a normal outcome, not an exception");
    }

    /** Same distinction, exercised directly against queryForObject (the one
     * overload where Spring itself would raise EmptyResultDataAccessException
     * for a single-result query matching 0 rows) via the REAL Application
     * Context bean - confirms it is NOT translated into
     * LegacyUnavailableException. */
    @Test
    void emptyResultDataAccessExceptionFromQueryForObjectIsNotTranslated() {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("itemCd", "NO-SUCH-SKU-8L-TEST");
        assertThrows(EmptyResultDataAccessException.class, () ->
                legacyNamedParameterJdbcTemplate.queryForObject(
                        "SELECT item_cd FROM ms_item WHERE item_cd = :itemCd", params, String.class));
    }
}
