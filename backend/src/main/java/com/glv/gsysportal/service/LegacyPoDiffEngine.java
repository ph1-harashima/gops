package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.LegacyPoDiffEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 7-C6 7章: structured Diff between a Baseline {@link LegacyPoSnapshot}
 * and the current one - never just a bare "fingerprint mismatch" (7-C6 7章's
 * explicit instruction). Pure function, no DB/Spring dependency - directly
 * unit-testable (7-C6 26章 "Header diff / Line changed / Line added / Line
 * removed / No change" regressions).
 */
final class LegacyPoDiffEngine {

    private LegacyPoDiffEngine() {
    }

    static List<LegacyPoDiffEntry> diff(LegacyPoSnapshot baseline, LegacyPoSnapshot current) {
        List<LegacyPoDiffEntry> entries = new ArrayList<>();

        addHeaderDiff(entries, "status", baseline.status(), current.status());
        addHeaderDiff(entries, "poType", baseline.poType(), current.poType());
        addHeaderDiff(entries, "supplierCode", baseline.supplierCode(), current.supplierCode());
        addHeaderDiff(entries, "brandCode", baseline.brandCode(), current.brandCode());
        addHeaderDiff(entries, "orderDate", str(baseline.orderDate()), str(current.orderDate()));
        addHeaderDiff(entries, "currency", baseline.currency(), current.currency());
        addHeaderDiff(entries, "delivWeek", baseline.delivWeek(), current.delivWeek());
        addHeaderDiff(entries, "delivDate", baseline.delivDate(), current.delivDate());
        addHeaderDiff(entries, "amtTtl", str(baseline.amtTtl()), str(current.amtTtl()));
        addHeaderDiff(entries, "lineCount", String.valueOf(baseline.lines().size()), String.valueOf(current.lines().size()));

        Map<String, LegacyPoSnapshotLine> baselineLines = toMap(baseline.lines());
        Map<String, LegacyPoSnapshotLine> currentLines = toMap(current.lines());

        for (Map.Entry<String, LegacyPoSnapshotLine> e : baselineLines.entrySet()) {
            String sku = e.getKey();
            LegacyPoSnapshotLine baseLine = e.getValue();
            LegacyPoSnapshotLine curLine = currentLines.get(sku);
            if (curLine == null) {
                entries.add(new LegacyPoDiffEntry("line", sku, summarize(baseLine), null, LegacyPoDiffEntry.TYPE_LINE_REMOVED));
                continue;
            }
            addLineDiff(entries, sku, "orderedQty", str(baseLine.orderedQty()), str(curLine.orderedQty()));
            addLineDiff(entries, sku, "unitPrice", str(baseLine.unitPrice()), str(curLine.unitPrice()));
        }
        for (Map.Entry<String, LegacyPoSnapshotLine> e : currentLines.entrySet()) {
            String sku = e.getKey();
            if (!baselineLines.containsKey(sku)) {
                entries.add(new LegacyPoDiffEntry("line", sku, null, summarize(e.getValue()), LegacyPoDiffEntry.TYPE_LINE_ADDED));
            }
        }

        return entries;
    }

    private static Map<String, LegacyPoSnapshotLine> toMap(List<LegacyPoSnapshotLine> lines) {
        Map<String, LegacyPoSnapshotLine> map = new LinkedHashMap<>();
        for (LegacyPoSnapshotLine line : lines) {
            map.put(line.skuCode(), line);
        }
        return map;
    }

    private static void addHeaderDiff(List<LegacyPoDiffEntry> entries, String field, String baselineValue, String currentValue) {
        if (!Objects.equals(baselineValue, currentValue)) {
            entries.add(new LegacyPoDiffEntry(field, null, baselineValue, currentValue, LegacyPoDiffEntry.TYPE_HEADER_CHANGED));
        }
    }

    private static void addLineDiff(List<LegacyPoDiffEntry> entries, String sku, String field, String baselineValue, String currentValue) {
        if (!Objects.equals(baselineValue, currentValue)) {
            entries.add(new LegacyPoDiffEntry(field, sku, baselineValue, currentValue, LegacyPoDiffEntry.TYPE_LINE_CHANGED));
        }
    }

    private static String summarize(LegacyPoSnapshotLine line) {
        return "qty=" + line.orderedQty() + ", unitPrice=" + line.unitPrice();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
