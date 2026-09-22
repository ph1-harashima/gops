package com.glv.gsysportal.repository.legacy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Phase 8-G 11章/20章's most important regression test: proves Arrival and
 * Warehouse Stock are never combined in a single SQL statement anywhere in
 * this codebase, in either direction. A plain, dependency-free text scan
 * (no Spring context, no DB) is deliberate - the constraint this protects
 * is "these two tables never appear together in one query", which is a
 * property of the SQL text itself, not of any runtime behavior a mocked or
 * live query could hide.
 */
class ArrivalWarehouseStockNotJoinedTest {

    private static final String[] ARRIVAL_QUERY_RESOURCES = {
            "legacy/ArrivalListQuery.sql", "legacy/ArrivalListCountQuery.sql",
    };
    private static final String[] WAREHOUSE_STOCK_QUERY_RESOURCES = {
            // Warehouse Stock Production-Scale Remediation (docs/real-data-audit/
            // gops-warehouse-stock-production-scale-remediation.md): the old
            // single-query WarehouseStockListQuery.sql was replaced by a
            // two-step pair - both checked here in its place.
            "legacy/WarehouseStockPageKeysQuery.sql", "legacy/WarehouseStockDetailByItemCodesQuery.sql",
            "legacy/WarehouseStockListCountQuery.sql", "legacy/WarehouseStockBySkuQuery.sql",
    };

    @Test
    void arrivalQueriesNeverReferenceMsStk() {
        for (String resource : ARRIVAL_QUERY_RESOURCES) {
            String sql = stripSqlComments(readSql(resource)).toLowerCase();
            assertFalse(sql.contains("ms_stk"), resource + " must never reference ms_stk (Warehouse Stock) outside comments");
        }
    }

    @Test
    void warehouseStockQueriesNeverReferenceTrArr() {
        for (String resource : WAREHOUSE_STOCK_QUERY_RESOURCES) {
            String sql = stripSqlComments(readSql(resource)).toLowerCase();
            assertFalse(sql.contains("tr_arr"), resource + " must never reference tr_arr (Arrival) outside comments");
        }
    }

    /** Strips {@code -- ...} line comments before the substring check below
     * - this class's own explanatory comments in the .sql files legitimately
     * name the OTHER table ("Deliberately does NOT join ms_stk anywhere")
     * as documentation; only an actual SQL reference (FROM/JOIN/WHERE) must
     * fail this test. */
    private static String stripSqlComments(String sql) {
        StringBuilder result = new StringBuilder();
        for (String line : sql.split("\n")) {
            int idx = line.indexOf("--");
            result.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return result.toString();
    }

    private static String readSql(String resourceName) {
        try {
            var resource = new ClassPathResource(resourceName);
            return new String(Files.readAllBytes(resource.getFile().toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            try (var is = new ClassPathResource(resourceName).getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException inner) {
                throw new java.io.UncheckedIOException("Failed to load " + resourceName, inner);
            }
        }
    }
}
