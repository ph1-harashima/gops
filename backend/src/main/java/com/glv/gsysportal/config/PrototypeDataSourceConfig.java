package com.glv.gsysportal.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Prototype DB datasource (READ/WRITE) - Draft / Workflow / Supplier Response / Audit.
 *
 * Marked {@code @Primary} so plain Spring Data JPA repositories (repository.prototype)
 * default to this DataSource without qualification.
 *
 * NOTE: as of this Step (Order Candidate List vertical slice), no
 * {@code @Entity} classes exist yet under com.glv.gsysportal.domain - this
 * config exists so the dual-DataSource wiring is proven end-to-end now, ahead
 * of the Prototype business tables that come in a later Step.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.glv.gsysportal.repository.prototype",
        entityManagerFactoryRef = "prototypeEntityManagerFactory",
        transactionManagerRef = "prototypeTransactionManager"
)
@EntityScan(basePackages = "com.glv.gsysportal.domain")
public class PrototypeDataSourceConfig {

    @Value("${app.prototype.datasource.jdbc-url}")
    private String jdbcUrl;

    @Value("${app.prototype.datasource.username}")
    private String username;

    @Value("${app.prototype.datasource.password}")
    private String password;

    @Value("${app.prototype.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${app.prototype.datasource.maximum-pool-size:5}")
    private int maximumPoolSize;

    @Bean
    @Primary
    public DataSource prototypeDataSource() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("prototype-readwrite-pool");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(maximumPoolSize);
        return new HikariDataSource(config);
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean prototypeEntityManagerFactory(EntityManagerFactoryBuilder builder,
                                                                                  DataSource prototypeDataSource) {
        // Returning the FactoryBean itself (not .getObject()) so Spring drives its
        // afterPropertiesSet() lifecycle before anything tries to use the EntityManagerFactory.
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "none"); // Flyway owns schema (later Step); nothing to manage yet
        return builder
                .dataSource(prototypeDataSource)
                .packages("com.glv.gsysportal.domain")
                .persistenceUnit("prototype")
                .properties(props)
                .jta(false)
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager prototypeTransactionManager(EntityManagerFactory prototypeEntityManagerFactory) {
        return new JpaTransactionManager(prototypeEntityManagerFactory);
    }
}
