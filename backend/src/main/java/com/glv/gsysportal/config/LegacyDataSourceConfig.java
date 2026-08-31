package com.glv.gsysportal.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Legacy G-SYS Adapter datasource - READ ONLY, always.
 *
 * Three-layer READ ONLY guarantee (Technical Design 4.1):
 *  1. DB user 'gsys_portal_ro' has GRANT SELECT only (demo-data/03-readonly-user.sql).
 *  2. This DataSource is configured with readOnly=true (JDBC driver / pool level).
 *  3. Every method in repository.legacy is annotated
 *     {@code @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")}.
 *
 * This DataSource is entirely separate from {@link PrototypeDataSourceConfig}'s
 * DataSource/TransactionManager - no bean, pool, or transaction is shared between
 * Legacy and Prototype, by design (Technical Design 1章/4.1章).
 */
@Configuration
public class LegacyDataSourceConfig {

    @Value("${app.legacy.datasource.jdbc-url}")
    private String jdbcUrl;

    @Value("${app.legacy.datasource.username}")
    private String username;

    @Value("${app.legacy.datasource.password}")
    private String password;

    @Value("${app.legacy.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${app.legacy.datasource.maximum-pool-size:5}")
    private int maximumPoolSize;

    @Bean(name = "legacyDataSource")
    public DataSource legacyDataSource() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("legacy-readonly-pool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(maximumPoolSize);
        // Layer 2 of the READ ONLY guarantee: the pool itself is read-only.
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    // Every consumer below is explicitly @Qualifier("legacyDataSource") rather than relying
    // on parameter-name matching, because prototypeDataSource is @Primary - without an
    // explicit qualifier, ambiguous-by-type injection silently falls back to @Primary and
    // the "Legacy" beans end up pointed at the Prototype Postgres DB instead of MySQL.
    // (This was caught for real during this implementation session - see final report.)

    @Bean
    public PlatformTransactionManager legacyTransactionManager(@Qualifier("legacyDataSource") DataSource legacyDataSource) {
        return new DataSourceTransactionManager(legacyDataSource);
    }

    @Bean
    public JdbcTemplate legacyJdbcTemplate(@Qualifier("legacyDataSource") DataSource legacyDataSource) {
        return new JdbcTemplate(legacyDataSource);
    }

    // Phase 8-L (Production Reliability Foundation): returns a
    // LegacyFailureTranslatingJdbcTemplate (a NamedParameterJdbcTemplate
    // subclass) instead of a plain one, so every repository.legacy class
    // that injects NamedParameterJdbcTemplate transparently gets Legacy
    // failure -> LegacyUnavailableException translation with zero changes
    // to those ~15 classes (Production Readiness Audit §10).
    @Bean
    public NamedParameterJdbcTemplate legacyNamedParameterJdbcTemplate(@Qualifier("legacyDataSource") DataSource legacyDataSource) {
        return new LegacyFailureTranslatingJdbcTemplate(legacyDataSource);
    }
}
