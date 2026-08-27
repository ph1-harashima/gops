package com.glv.gsysportal.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Flyway migration - Prototype PostgreSQL ONLY.
 *
 * Spring Boot's {@code FlywayAutoConfiguration} is excluded (application.yml)
 * so there is no implicit "pick whichever DataSource looks primary" behavior
 * that could accidentally target the wrong DataSource in a dual-DataSource
 * application. This bean is wired explicitly to {@code prototypeDataSource}
 * ({@link PrototypeDataSourceConfig}) and NEVER to {@code legacyDataSource}
 * ({@link LegacyDataSourceConfig}) - Legacy (Demo or otherwise) is never
 * migrated by this application (implementation instructions 2章).
 *
 * By the time this bean is created, {@code SafetyGuardEnvironmentPostProcessor}
 * has already validated the active profile and both JDBC URLs (it runs during
 * environment post-processing, strictly before any bean - including this one -
 * is instantiated). Migration only ever proceeds after that gate has passed.
 */
@Configuration
public class PrototypeFlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(PrototypeFlywayConfig.class);

    @Bean
    @DependsOn("prototypeDataSource")
    public Flyway prototypeFlyway(@Qualifier("prototypeDataSource") DataSource prototypeDataSource) throws SQLException {
        logMigrationTarget(prototypeDataSource);

        Flyway flyway = Flyway.configure()
                .dataSource(prototypeDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();
        flyway.migrate();
        return flyway;
    }

    /**
     * Instructions 2章: "Migration実行前に対象JDBC URL / DB Product / DB Nameを
     * logへ表示" - logged at INFO so it is always visible, not hidden behind
     * a debug flag, before a single migration statement executes.
     */
    private void logMigrationTarget(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            log.info("Flyway migration target - JDBC URL: {}, DB Product: {} {}, Catalog: {}",
                    metaData.getURL(),
                    metaData.getDatabaseProductName(),
                    metaData.getDatabaseProductVersion(),
                    connection.getCatalog());
        }
    }
}
