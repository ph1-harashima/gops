package com.glv.gsysportal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyHeaderRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoConcurrencyLineRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Phase 7-C6 26章: pure "Fingerprint stability" regression - proves 7-C6
 * 3章's core promise: same Business Data + different DB fetch order = same
 * Fingerprint; a real content change = a different Fingerprint. */
class LegacyPoFingerprintCalculatorTest {

    private final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.json.JsonMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private static final LegacyPoConcurrencyHeaderRow HEADER = new LegacyPoConcurrencyHeaderRow(
            "PO-X", "OFFICIAL", null, "SUP_A", "BR_A",
            LocalDate.of(2026, 8, 1), "JPY", "35", "2026-08-24", new BigDecimal("6000.00"));

    private String fingerprintOf(List<LegacyPoConcurrencyLineRow> lines) throws Exception {
        LegacyPoSnapshot snapshot = LegacyPoSnapshotFactory.build(HEADER, lines);
        return LegacyPoFingerprintCalculator.compute(objectMapper.writeValueAsString(snapshot));
    }

    @Test
    void sameDataDifferentFetchOrderProducesSameFingerprint() throws Exception {
        LegacyPoConcurrencyLineRow tent = new LegacyPoConcurrencyLineRow("OD-TENT-001", 5, new BigDecimal("1000.00000"));
        LegacyPoConcurrencyLineRow chair = new LegacyPoConcurrencyLineRow("OD-CHAIR-001", 2, new BigDecimal("500.00000"));

        String fp1 = fingerprintOf(List.of(tent, chair));
        String fp2 = fingerprintOf(List.of(chair, tent));

        assertEquals(fp1, fp2);
    }

    @Test
    void qtyChangeProducesDifferentFingerprint() throws Exception {
        String fp1 = fingerprintOf(List.of(new LegacyPoConcurrencyLineRow("OD-TENT-001", 5, new BigDecimal("1000.00000"))));
        String fp2 = fingerprintOf(List.of(new LegacyPoConcurrencyLineRow("OD-TENT-001", 6, new BigDecimal("1000.00000"))));

        assertNotEquals(fp1, fp2);
    }

    @Test
    void priceChangeProducesDifferentFingerprint() throws Exception {
        String fp1 = fingerprintOf(List.of(new LegacyPoConcurrencyLineRow("OD-TENT-001", 5, new BigDecimal("1000.00000"))));
        String fp2 = fingerprintOf(List.of(new LegacyPoConcurrencyLineRow("OD-TENT-001", 5, new BigDecimal("1100.00000"))));

        assertNotEquals(fp1, fp2);
    }

    @Test
    void addingALineProducesDifferentFingerprint() throws Exception {
        LegacyPoConcurrencyLineRow tent = new LegacyPoConcurrencyLineRow("OD-TENT-001", 5, new BigDecimal("1000.00000"));
        LegacyPoConcurrencyLineRow chair = new LegacyPoConcurrencyLineRow("OD-CHAIR-001", 2, new BigDecimal("500.00000"));

        String fp1 = fingerprintOf(List.of(tent));
        String fp2 = fingerprintOf(List.of(tent, chair));

        assertNotEquals(fp1, fp2);
    }

    @Test
    void fingerprintIsSha256HexEncoded64Chars() throws Exception {
        String fp = fingerprintOf(List.of());
        assertEquals(64, fp.length());
        assertEquals(fp, fp.toLowerCase());
    }
}
