package com.glv.gsysportal.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Implementation instructions 16章 "Flyway Migration Test": proves the
 * Prototype-only migrations (V1..V6, {@link PrototypeFlywayConfig}) were
 * actually applied to the local Prototype Postgres and that Flyway is bound
 * exclusively to {@code prototypeDataSource} - never to {@code legacyDataSource}
 * (Technical Design 9章 / implementation instructions 2章). V6 is Step 3's
 * addition (prototype_po_no_seq + ORDER_RETURNED_TO_DRAFT event type).
 */
@SpringBootTest
@ActiveProfiles("test")
class PrototypeFlywayMigrationTest {

    @Autowired
    @Qualifier("prototypeDataSource")
    private DataSource prototypeDataSource;

    @Test
    void allMigrationsAppliedSuccessfully() {
        JdbcTemplate jdbc = new JdbcTemplate(prototypeDataSource);
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank", String.class);
        // V6 (Step 3): prototype_po_no_seq + ORDER_RETURNED_TO_DRAFT event type.
        assertEquals(List.of("1", "2", "3", "4", "5", "6"), versions);
    }

    @Test
    void expectedTablesExistOnPrototypePostgres() {
        JdbcTemplate jdbc = new JdbcTemplate(prototypeDataSource);
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name", String.class);
        for (String expected : List.of("portal_order", "portal_order_detail", "audit_event", "portal_user")) {
            assertTrue(tables.contains(expected), "Expected table missing: " + expected + ", got: " + tables);
        }
    }

    @Test
    void demoPortalUsersWereSeeded() {
        JdbcTemplate jdbc = new JdbcTemplate(prototypeDataSource);
        List<String> usernames = jdbc.queryForList("SELECT username FROM portal_user ORDER BY username", String.class);
        assertEquals(List.of("purchase01", "sales_admin", "sys_admin"), usernames);
    }

    @Test
    void prototypePoNoSequenceExists() {
        JdbcTemplate jdbc = new JdbcTemplate(prototypeDataSource);
        List<String> sequences = jdbc.queryForList(
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public'", String.class);
        assertTrue(sequences.contains("prototype_po_no_seq"));
    }
}
