package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.LegacyPoDiffEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 7-C6 26章: pure "Header diff / Line changed / Line added / Line
 * removed / No change" regressions - no Spring context, no DB. */
class LegacyPoDiffEngineTest {

    private static LegacyPoSnapshot snapshot(String status, List<LegacyPoSnapshotLine> lines) {
        return new LegacyPoSnapshot("PO-X", status, null, "SUP_A", "BR_A",
                LocalDate.of(2026, 8, 1), "JPY", "35", "2026-08-24", new BigDecimal("6000.00"), lines);
    }

    @Test
    void identicalSnapshotsProduceNoDiffEntries() {
        List<LegacyPoSnapshotLine> lines = List.of(new LegacyPoSnapshotLine("OD-TENT-001", 5, new BigDecimal("1000")));
        LegacyPoSnapshot a = snapshot("OFFICIAL", lines);
        LegacyPoSnapshot b = snapshot("OFFICIAL", lines);

        assertTrue(LegacyPoDiffEngine.diff(a, b).isEmpty());
    }

    @Test
    void headerFieldChangeProducesHeaderChangedEntry() {
        LegacyPoSnapshot baseline = snapshot("OFFICIAL", List.of());
        LegacyPoSnapshot current = snapshot("CANCELLED", List.of());

        List<LegacyPoDiffEntry> diffs = LegacyPoDiffEngine.diff(baseline, current);

        assertEquals(1, diffs.size());
        LegacyPoDiffEntry entry = diffs.get(0);
        assertEquals("status", entry.field());
        assertEquals(LegacyPoDiffEntry.TYPE_HEADER_CHANGED, entry.diffType());
        assertEquals("OFFICIAL", entry.baselineValue());
        assertEquals("CANCELLED", entry.currentValue());
        assertEquals(null, entry.skuCode());
    }

    @Test
    void qtyChangeProducesLineChangedEntry() {
        LegacyPoSnapshot baseline = snapshot("OFFICIAL", List.of(new LegacyPoSnapshotLine("OD-TENT-001", 5, new BigDecimal("1000"))));
        LegacyPoSnapshot current = snapshot("OFFICIAL", List.of(new LegacyPoSnapshotLine("OD-TENT-001", 8, new BigDecimal("1000"))));

        List<LegacyPoDiffEntry> diffs = LegacyPoDiffEngine.diff(baseline, current);

        assertEquals(1, diffs.size());
        assertEquals("orderedQty", diffs.get(0).field());
        assertEquals("OD-TENT-001", diffs.get(0).skuCode());
        assertEquals(LegacyPoDiffEntry.TYPE_LINE_CHANGED, diffs.get(0).diffType());
        assertEquals("5", diffs.get(0).baselineValue());
        assertEquals("8", diffs.get(0).currentValue());
    }

    @Test
    void priceChangeProducesLineChangedEntry() {
        LegacyPoSnapshot baseline = snapshot("OFFICIAL", List.of(new LegacyPoSnapshotLine("OD-TENT-001", 5, new BigDecimal("1000"))));
        LegacyPoSnapshot current = snapshot("OFFICIAL", List.of(new LegacyPoSnapshotLine("OD-TENT-001", 5, new BigDecimal("1200"))));

        List<LegacyPoDiffEntry> diffs = LegacyPoDiffEngine.diff(baseline, current);

        assertEquals(1, diffs.size());
        assertEquals("unitPrice", diffs.get(0).field());
        assertEquals(LegacyPoDiffEntry.TYPE_LINE_CHANGED, diffs.get(0).diffType());
    }

    @Test
    void newLineInCurrentProducesLineAddedEntry() {
        LegacyPoSnapshot baseline = snapshot("OFFICIAL", List.of(new LegacyPoSnapshotLine("OD-TENT-001", 5, new BigDecimal("1000"))));
        LegacyPoSnapshot current = snapshot("OFFICIAL", List.of(
                new LegacyPoSnapshotLine("OD-TENT-001", 5, new BigDecimal("1000")),
                new LegacyPoSnapshotLine("OD-CHAIR-001", 2, new BigDecimal("500"))));

        List<LegacyPoDiffEntry> diffs = LegacyPoDiffEngine.diff(baseline, current);

        // lineCount also changes (1 -> 2), plus the new line itself.
        assertEquals(2, diffs.size());
        assertTrue(diffs.stream().anyMatch(d -> d.diffType().equals(LegacyPoDiffEntry.TYPE_LINE_ADDED)
                && "OD-CHAIR-001".equals(d.skuCode()) && d.baselineValue() == null));
        assertTrue(diffs.stream().anyMatch(d -> d.field().equals("lineCount")));
    }

    @Test
    void missingLineInCurrentProducesLineRemovedEntry() {
        LegacyPoSnapshot baseline = snapshot("OFFICIAL", List.of(
                new LegacyPoSnapshotLine("OD-TENT-001", 5, new BigDecimal("1000")),
                new LegacyPoSnapshotLine("OD-CHAIR-001", 2, new BigDecimal("500"))));
        LegacyPoSnapshot current = snapshot("OFFICIAL", List.of(new LegacyPoSnapshotLine("OD-TENT-001", 5, new BigDecimal("1000"))));

        List<LegacyPoDiffEntry> diffs = LegacyPoDiffEngine.diff(baseline, current);

        assertTrue(diffs.stream().anyMatch(d -> d.diffType().equals(LegacyPoDiffEntry.TYPE_LINE_REMOVED)
                && "OD-CHAIR-001".equals(d.skuCode()) && d.currentValue() == null));
        assertTrue(diffs.stream().anyMatch(d -> d.field().equals("lineCount")));
    }
}
