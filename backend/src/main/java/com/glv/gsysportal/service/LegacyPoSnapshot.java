package com.glv.gsysportal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Phase 7-C6 2章/3章: the Canonical Snapshot of one Legacy Official PO -
 * exactly the fields {@code LegacyPoConcurrencyReadRepository} reads, in a
 * form that is (a) stable across independent fetches of the same underlying
 * data (Canonical Ordering: {@link #lines()} is always sorted ascending by
 * {@link LegacyPoSnapshotLine#skuCode()}, regardless of DB fetch order) and
 * (b) directly Jackson-serializable, since this exact shape is both the
 * persisted {@code legacy_po_baseline.snapshot_json} and the input to
 * {@link LegacyPoFingerprintCalculator}/{@link LegacyPoDiffEngine}.
 *
 * <p>Deliberately does NOT include {@code lineCount} as its own field - it is
 * always {@code lines().size()}, so a stored duplicate would just be a second
 * source of truth for the same value (7-C6 2章 does list "line count" as a
 * candidate, but {@link LegacyPoDiffEngine} derives it instead of storing it).
 */
public record LegacyPoSnapshot(
        String officialPoNo,
        String status,
        String poType,
        String supplierCode,
        String brandCode,
        LocalDate orderDate,
        String currency,
        String delivWeek,
        String delivDate,
        BigDecimal amtTtl,
        List<LegacyPoSnapshotLine> lines) {
}
