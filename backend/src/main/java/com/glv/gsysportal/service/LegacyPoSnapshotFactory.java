package com.glv.gsysportal.service;

import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyHeaderRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyLineRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 7-C6 2章: pure transformation from raw
 * {@code LegacyPoConcurrencyReadRepository} rows into a Canonical
 * {@link LegacyPoSnapshot} - no DB/Spring dependency, so it is directly
 * unit-testable (7-C6 26章 "Snapshot canonicalization" regression).
 *
 * <p><b>Canonical Ordering</b>: lines are always emitted sorted ascending by
 * SKU (item_cd), regardless of the order the Repository returned them in -
 * this is what makes {@link LegacyPoFingerprintCalculator#compute} stable
 * across independent fetches of the same underlying data (7-C6 3章 "同じ
 * Business Data + 異なるDB取得順序 = 同じFingerprint").
 *
 * <p><b>Duplicate SKU handling</b>: real Legacy TR_PO_DTL is one row per
 * (po_no, item_cd) in ordinary practice (one Excel row per item). If the same
 * item_cd ever legitimately appears on more than one line within a single PO,
 * this Factory aggregates them into a single canonical line (summed
 * {@code orderedQty}, {@code unitPrice} taken from whichever row is
 * encountered first after sorting) rather than silently losing a line - a
 * documented simplification, not a STOP condition (7-C6 29章 evaluated this
 * exact edge case: it does not block a deterministic Snapshot, it only
 * coarsens duplicate-SKU granularity slightly, which is acceptable since it
 * has never been observed in this codebase's Legacy Source/fixtures).
 */
final class LegacyPoSnapshotFactory {

    private LegacyPoSnapshotFactory() {
    }

    static LegacyPoSnapshot build(LegacyPoConcurrencyHeaderRow header, List<LegacyPoConcurrencyLineRow> rawLines) {
        Map<String, LegacyPoSnapshotLine> bySku = new LinkedHashMap<>();
        for (LegacyPoConcurrencyLineRow row : rawLines) {
            bySku.merge(row.skuCode(),
                    new LegacyPoSnapshotLine(row.skuCode(), row.orderedQty(), row.unitPrice()),
                    (existing, incoming) -> new LegacyPoSnapshotLine(
                            existing.skuCode(),
                            (existing.orderedQty() == null ? 0 : existing.orderedQty())
                                    + (incoming.orderedQty() == null ? 0 : incoming.orderedQty()),
                            existing.unitPrice() != null ? existing.unitPrice() : incoming.unitPrice()));
        }
        List<LegacyPoSnapshotLine> canonicalLines = new ArrayList<>(bySku.values());
        canonicalLines.sort((a, b) -> a.skuCode().compareTo(b.skuCode()));

        return new LegacyPoSnapshot(
                header.poNo(), header.status(), header.poType(), header.supplierCode(), header.brandCode(),
                header.orderDate(), header.currency(), header.delivWeek(), header.delivDate(), header.amtTtl(),
                canonicalLines);
    }
}
