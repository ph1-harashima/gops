package com.glv.gsysportal.service.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7-C2A 10章: proves {@link OfficialPoExcelGenerator}'s output matches
 * the exact Cell Contract {@code PrOfficialPoImportBatch} expects
 * (row/col constants independently re-declared here from
 * docs/official-po-integration-detailed-design.md 3章 - NOT imported from the
 * Generator, so the two can never silently drift together undetected; and
 * NOT executing any Legacy code, per this Phase's "Legacy Source変更禁止/
 * Legacy Test Fileは変更しない" constraint). Pure unit test, no Spring
 * context, no filesystem I/O - matches PoPreviewValidatorTest's convention.
 *
 * <p>Uses a Test-only Official PO No. (7-C2A 9章: "G-SYS Validationを通る
 * 形式のTest-only Official PO No.を使用してよい") - never a value any
 * Production/Demo code path could persist as a real one.
 */
class OfficialPoExcelGeneratorContractTest {

    // Re-declared independently from PrOfficialPoImportBatch.java's
    // ROW_IDX_ / COL_IDX_ constants (0-based) - see class Javadoc.
    private static final int ROW_PO_HEADER_1 = 1;
    private static final int COL_PO_HEADER_1 = 8;
    private static final int ROW_PO_HEADER_1_1 = 6;
    private static final int COL_PO_HEADER_1_1 = 7;
    private static final int ROW_ORDR_DATE = 2;
    private static final int COL_ORDR_DATE = 9;
    private static final int ROW_PO_NO = 3;
    private static final int COL_PO_NO = 9;
    private static final int ROW_BRAND_NAME = 4;
    private static final int COL_BRAND_NAME = 9;
    private static final int ROW_PO_HEADER_2 = 13;
    private static final int COL_PO_HEADER_2 = 1;
    private static final int ROW_DELIV = 14;
    private static final int COL_DELIV_WEEK = 3;
    private static final int COL_DELIV_DATE = 4;
    private static final int COL_SHIP_VIA = 6;
    private static final int COL_SHIP_TERM = 8;
    private static final int COL_PAYMENT_TERM = 10;
    private static final int ROW_ITEM_HEADER = 16;
    private static final int COL_NO = 1;
    private static final int COL_ITEM_CD = 2;
    private static final int COL_SERIES = 3;
    private static final int COL_MODEL_NO = 4;
    private static final int COL_MODEL = 5;
    private static final int COL_COLOR = 6;
    private static final int COL_DESCRIPTION = 7;
    private static final int COL_QTY_PO = 8;
    private static final int COL_PRC_UNIT = 9;
    private static final int COL_AMT_LINE = 10;
    private static final int COL_CTRY_ORG = 11;
    private static final int COL_BOX_HEIGHT = 12;
    private static final int COL_BOX_WIDTH = 13;
    private static final int COL_BOX_DEPTH = 14;
    private static final int COL_WEIGHT = 15;
    private static final int FIRST_ITEM_ROW = ROW_ITEM_HEADER + 1;

    // Test-only PO No. matching BusinessLogicUtil.getSupplierCd/getBrandCd/
    // getIdCd's substring positions (chars 0-3=Supplier, 5-7=Brand, 9-10=ID) -
    // never a real Official PO No.
    private static final String TEST_PO_NO = "TSUP-TBR-99";

    private final OfficialPoExcelGenerator generator = new OfficialPoExcelGenerator();

    private static OfficialPoExcelInput.Line line(String itemCode, String currencySymbol) {
        return new OfficialPoExcelInput.Line(itemCode, "SERIES-A", "MODEL-001", "Model Name", "Black",
                "A description", 10, new BigDecimal("32.40"), currencySymbol,
                "Vietnam", new BigDecimal("20.5"), new BigDecimal("15.0"), new BigDecimal("10.0"), new BigDecimal("1.2"));
    }

    private Sheet generateAndRead(OfficialPoExcelInput input) throws IOException {
        byte[] bytes = generator.generate(input);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            return wb.getSheetAt(0);
        }
    }

    private static String str(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(colIdx);
        return cell == null ? null : cell.getStringCellValue();
    }

    @Test
    void headerCellsMatchPrOfficialPoImportBatchContract() throws IOException {
        OfficialPoExcelInput input = new OfficialPoExcelInput(
                TEST_PO_NO, "TSUP", "TBR", "Test Brand",
                LocalDate.of(2026, 9, 1), "WK40", "2026/09/28", "Air", "EXW UK", "100% once",
                List.of(line("ITEM-001", "$")));
        Sheet sheet = generateAndRead(input);

        // File Format Validation header strings (PrOfficialPoImportBatch.java:280-341).
        assertTrue(str(sheet, ROW_PO_HEADER_1, COL_PO_HEADER_1).contains("PURCHASE"));
        assertTrue(str(sheet, ROW_PO_HEADER_1_1, COL_PO_HEADER_1_1).contains("ORDER TO"));
        assertEquals("PURCHASER", str(sheet, ROW_PO_HEADER_2, COL_PO_HEADER_2));
        assertEquals("No", str(sheet, ROW_ITEM_HEADER, COL_NO));
        assertEquals("CntryOrg1", str(sheet, ROW_ITEM_HEADER, COL_CTRY_ORG));
        assertEquals("Box height[cm]", str(sheet, ROW_ITEM_HEADER, COL_BOX_HEIGHT));
        assertEquals("Box width[cm]", str(sheet, ROW_ITEM_HEADER, COL_BOX_WIDTH));
        assertEquals("Box depth[cm]", str(sheet, ROW_ITEM_HEADER, COL_BOX_DEPTH));
        assertEquals("Weight[kg]", str(sheet, ROW_ITEM_HEADER, COL_WEIGHT));

        // Order Date must be a real Excel date and never contain "init"
        // (Official-vs-Initial discriminator, PrOfficialPoImportBatch.java:352-355).
        Cell orderDateCell = sheet.getRow(ROW_ORDR_DATE).getCell(COL_ORDR_DATE);
        assertTrue(org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(orderDateCell));

        assertEquals(TEST_PO_NO, str(sheet, ROW_PO_NO, COL_PO_NO));
        assertFalse(str(sheet, ROW_PO_NO, COL_PO_NO).toLowerCase().contains("init"));
        assertEquals("Test Brand", str(sheet, ROW_BRAND_NAME, COL_BRAND_NAME));

        assertEquals("WK40", str(sheet, ROW_DELIV, COL_DELIV_WEEK));
        assertEquals("2026/09/28", str(sheet, ROW_DELIV, COL_DELIV_DATE));
        assertEquals("Air", str(sheet, ROW_DELIV, COL_SHIP_VIA));
        assertEquals("EXW UK", str(sheet, ROW_DELIV, COL_SHIP_TERM));
        assertEquals("100% once", str(sheet, ROW_DELIV, COL_PAYMENT_TERM));
    }

    @Test
    void poNoSubstringPositionsMatchBusinessLogicUtilContract() {
        // Mirrors BusinessLogicUtil.getSupplierCd/getBrandCd/getIdCd exactly
        // (phasep-gulliver/gulliver/src/main/java/jp/ne/glv/utilities/BusinessLogicUtil.java:1028-1069) -
        // re-implemented here rather than calling Legacy code (Legacy Source
        // is never depended on from Backend, Technical Design 4.1).
        assertEquals("TSUP", TEST_PO_NO.substring(0, 4).toUpperCase());
        assertEquals("TBR", TEST_PO_NO.substring(5, 8).toUpperCase());
        assertEquals("99", TEST_PO_NO.substring(9, 11));
    }

    @Test
    void itemLinesStartAtRow17AndMatchColumnContract() throws IOException {
        OfficialPoExcelInput input = new OfficialPoExcelInput(
                TEST_PO_NO, "TSUP", "TBR", "Test Brand",
                LocalDate.of(2026, 9, 1), "WK40", "2026/09/28", "Air", "EXW UK", "100% once",
                List.of(line("ITEM-001", "$"), line("ITEM-002", "$")));
        Sheet sheet = generateAndRead(input);

        assertEquals("ITEM-001", str(sheet, FIRST_ITEM_ROW, COL_ITEM_CD));
        assertEquals("ITEM-002", str(sheet, FIRST_ITEM_ROW + 1, COL_ITEM_CD));
        assertEquals("SERIES-A", str(sheet, FIRST_ITEM_ROW, COL_SERIES));
        assertEquals("MODEL-001", str(sheet, FIRST_ITEM_ROW, COL_MODEL_NO));
        assertEquals("Model Name", str(sheet, FIRST_ITEM_ROW, COL_MODEL));
        assertEquals("Black", str(sheet, FIRST_ITEM_ROW, COL_COLOR));
        assertEquals("A description", str(sheet, FIRST_ITEM_ROW, COL_DESCRIPTION));
        assertEquals(10.0, sheet.getRow(FIRST_ITEM_ROW).getCell(COL_QTY_PO).getNumericCellValue());
        assertEquals(32.40, sheet.getRow(FIRST_ITEM_ROW).getCell(COL_PRC_UNIT).getNumericCellValue(), 0.001);
        assertEquals("Vietnam", str(sheet, FIRST_ITEM_ROW, COL_CTRY_ORG));
    }

    @Test
    void currencyIsEncodedInUnitPriceCellNumberFormat_notCellValue() throws IOException {
        // The single most important, easy-to-get-wrong Contract detail
        // (docs/official-po-integration-detailed-design.md 3.4章): Currency is
        // read by AbstImportBatch.getCcyCode() from Cell Number Format, never
        // from a text/value Cell. This asserts the generated format string
        // contains "$" immediately followed by the symbol, mirroring
        // getCcyCode()'s own matcher
        // (phasep-gulliver/gulliver/src/main/java/jp/ne/glv/batch/core/AbstImportBatch.java:1087-1098:
        // "ccySymbolCellString.indexOf(\"$\" + ccySymbol) >= 0").
        OfficialPoExcelInput input = new OfficialPoExcelInput(
                TEST_PO_NO, "TSUP", "TBR", "Test Brand",
                LocalDate.of(2026, 9, 1), "WK40", "2026/09/28", "Air", "EXW UK", "100% once",
                List.of(line("ITEM-001", "¥"))); // Yen symbol
        Sheet sheet = generateAndRead(input);

        Cell unitPriceCell = sheet.getRow(FIRST_ITEM_ROW).getCell(COL_PRC_UNIT);
        String format = unitPriceCell.getCellStyle().getDataFormatString();
        assertTrue(format.contains("$¥"), "Expected the Number Format to embed \"$¥\" (getCcyCode()'s matcher), got: " + format);
    }
}
