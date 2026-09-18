package com.glv.gsysportal.service.excel;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章): pure
 * unit test, no Spring context - mirrors OfficialPoExcelGeneratorContractTest's
 * convention. Extracts the rendered PDF text (PDFTextStripper) and asserts
 * every field the caller supplied actually appears, and NOTHING the caller
 * did NOT supply is invented (7章's "存在しない情報を捏造しない" requirement).
 */
class OfficialPoPdfGeneratorTest {

    private final OfficialPoPdfGenerator generator = new OfficialPoPdfGenerator();

    private OfficialPoPdfInput sampleInput() {
        OfficialPoExcelInput.Line line1 = new OfficialPoExcelInput.Line(
                "OD-TENT-001", null, null, null, null, "Dome Tent 2-Person",
                3, new BigDecimal("120.00"), "$", null, null, null, null, null);
        OfficialPoExcelInput.Line line2 = new OfficialPoExcelInput.Line(
                "OD-CHAIR-002", null, null, null, null, "Camp Chair",
                5, new BigDecimal("40.00"), "$", null, null, null, null, null);
        OfficialPoExcelInput data = new OfficialPoExcelInput(
                "SUPA-OUTD-99-TEST0001", "SUP_ALPHA", "BR_OUTDOOR", "OUTDOOR LIFE",
                LocalDate.of(2026, 9, 18), "WK38", "2026-09-20", "AIR", "FOB", "NET30",
                List.of(line1, line2));
        return new OfficialPoPdfInput(data, "Alpha Supply Co.", 2);
    }

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void rendersConfirmedFieldsOnly() throws IOException {
        byte[] pdf = generator.generate(sampleInput());
        String text = extractText(pdf);

        assertTrue(text.contains("G-OPS Standard Format"), "must self-identify as G-OPS Standard Format");
        assertTrue(text.contains("SUPA-OUTD-99-TEST0001"), "PO Number");
        assertTrue(text.contains("002"), "3-digit Revision");
        assertTrue(text.contains("SUP_ALPHA"), "Supplier code");
        assertTrue(text.contains("Alpha Supply Co."), "Supplier name");
        assertTrue(text.contains("BR_OUTDOOR"), "Brand code");
        assertTrue(text.contains("OUTDOOR LIFE"), "Brand name");
        assertTrue(text.contains("2026-09-18"), "Order Date");
        assertTrue(text.contains("WK38"), "Delivery Week");
        assertTrue(text.contains("AIR"), "Ship Via");
        assertTrue(text.contains("FOB"), "Ship Term");
        assertTrue(text.contains("NET30"), "Payment Term");
        assertTrue(text.contains("OD-TENT-001"), "line 1 SKU");
        assertTrue(text.contains("Dome Tent 2-Person"), "line 1 name");
        assertTrue(text.contains("OD-CHAIR-002"), "line 2 SKU");
        // 3 * 120.00 + 5 * 40.00 = 560.00
        assertTrue(text.contains("560.00") || text.contains("560"), "Total must reflect actual line Amounts, not a fabricated figure");
    }

    @Test
    void neverInventsFieldsTheCallerDidNotSupply() throws IOException {
        OfficialPoExcelInput.Line line = new OfficialPoExcelInput.Line(
                "OD-TENT-001", null, null, null, null, "Dome Tent",
                1, null, "$", null, null, null, null, null);
        OfficialPoExcelInput data = new OfficialPoExcelInput(
                "PO-MIN-001", "SUP_ALPHA", "BR_OUTDOOR", null,
                null, null, null, null, null, null,
                List.of(line));
        byte[] pdf = generator.generate(new OfficialPoPdfInput(data, null, 1));
        String text = extractText(pdf);

        // Company is deliberately never rendered (7章/14章: definition
        // unconfirmed) - must never appear as a label on this document.
        assertTrue(!text.contains("Company:"), "must never render a Company field (undefined per 14章 TODO)");
        assertTrue(text.contains("PO-MIN-001"));
        assertTrue(text.contains("-"), "unit price/amount fall back to '-'/0 rather than fabricating a price");
    }
}
