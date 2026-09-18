package com.glv.gsysportal.service.excel;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Gap Analysis B-3/B-4 (docs/gulliver-20260917-phase1-gap-analysis.md 7章). */
class OfficialPoFileNamingTest {

    @Test
    void buildsExpectedFormatWithZeroPaddedRevision() {
        String name = OfficialPoFileNaming.buildFileName("SUP_ALPHA", "BR_OUTDOOR",
                LocalDate.of(2026, 9, 18), "PO-1234", 2, "xlsx");

        assertEquals("OfficialPO_SUP_ALPHA_BR_OUTDOOR_20260918_PO-1234_002.xlsx", name);
    }

    @Test
    void sanitizesFilesystemUnsafeCharactersInPoNo() {
        String name = OfficialPoFileNaming.buildFileName("SUP_ALPHA", "BR_OUTDOOR",
                LocalDate.of(2026, 9, 18), "PO/1234:*?\"<>| test", 1, "pdf");

        assertEquals("OfficialPO_SUP_ALPHA_BR_OUTDOOR_20260918_PO_1234_test_001.pdf", name);
    }

    @Test
    void fallsBackWhenPoNoIsNull_neverBlankSegment() {
        String name = OfficialPoFileNaming.buildFileName("SUP_ALPHA", "BR_OUTDOOR",
                LocalDate.of(2026, 9, 18), null, 1, "xlsx");

        assertEquals("OfficialPO_SUP_ALPHA_BR_OUTDOOR_20260918_UNASSIGNED_001.xlsx", name);
    }

    @Test
    void revisionIsAlwaysThreeDigitsEvenPastNine() {
        String name = OfficialPoFileNaming.buildFileName("SUP_ALPHA", "BR_OUTDOOR",
                LocalDate.of(2026, 9, 18), "PO-1", 12, "xlsx");

        assertEquals("OfficialPO_SUP_ALPHA_BR_OUTDOOR_20260918_PO-1_012.xlsx", name);
    }
}
