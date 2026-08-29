package com.glv.gsysportal.service;

import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyHeaderRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyLineRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Phase 7-C6 26章: pure "Snapshot canonicalization" regression - no Spring
 * context, no DB. */
class LegacyPoSnapshotFactoryTest {

    private static final LegacyPoConcurrencyHeaderRow HEADER = new LegacyPoConcurrencyHeaderRow(
            "PO-X", "OFFICIAL", null, "SUP_A", "BR_A",
            LocalDate.of(2026, 8, 1), "JPY", "35", "2026-08-24", new BigDecimal("6000.00"));

    @Test
    void linesAreCanonicallySortedBySkuRegardlessOfInputOrder() {
        List<LegacyPoConcurrencyLineRow> rawLines = List.of(
                new LegacyPoConcurrencyLineRow("OD-CHAIR-001", 2, new BigDecimal("500.00000")),
                new LegacyPoConcurrencyLineRow("OD-TENT-001", 5, new BigDecimal("1000.00000")));

        LegacyPoSnapshot snapshot = LegacyPoSnapshotFactory.build(HEADER, rawLines);

        assertEquals(2, snapshot.lines().size());
        assertEquals("OD-CHAIR-001", snapshot.lines().get(0).skuCode());
        assertEquals("OD-TENT-001", snapshot.lines().get(1).skuCode());
    }

    @Test
    void duplicateSkuLinesAreAggregatedByQtySum() {
        List<LegacyPoConcurrencyLineRow> rawLines = List.of(
                new LegacyPoConcurrencyLineRow("OD-TENT-001", 2, new BigDecimal("1000.00000")),
                new LegacyPoConcurrencyLineRow("OD-TENT-001", 3, null));

        LegacyPoSnapshot snapshot = LegacyPoSnapshotFactory.build(HEADER, rawLines);

        assertEquals(1, snapshot.lines().size());
        assertEquals(5, snapshot.lines().get(0).orderedQty());
        assertEquals(new BigDecimal("1000.00000"), snapshot.lines().get(0).unitPrice());
    }

    @Test
    void headerFieldsArePassedThroughVerbatim() {
        LegacyPoSnapshot snapshot = LegacyPoSnapshotFactory.build(HEADER, List.of());

        assertEquals("PO-X", snapshot.officialPoNo());
        assertEquals("OFFICIAL", snapshot.status());
        assertEquals("SUP_A", snapshot.supplierCode());
        assertEquals("BR_A", snapshot.brandCode());
        assertEquals(LocalDate.of(2026, 8, 1), snapshot.orderDate());
        assertEquals("JPY", snapshot.currency());
        assertEquals("35", snapshot.delivWeek());
        assertEquals("2026-08-24", snapshot.delivDate());
        assertEquals(new BigDecimal("6000.00"), snapshot.amtTtl());
        assertEquals(0, snapshot.lines().size());
    }
}
