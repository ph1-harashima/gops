package com.glv.gsysportal.service.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Phase 7-C2A 8章: Excel Generator Foundation. A pure function - Portal
 * Order data in, {@code byte[]} out. Never touches the filesystem, never
 * talks to Legacy, never knows about the Import Folder (that responsibility
 * belongs to a future Integration Worker, 7-C2-Design 16章 - explicitly kept
 * out of the Generator, 7-C2A 8章: "Legacy File SystemへのWrite責任を
 * Generatorに持たせない").
 *
 * <p>Cell layout mirrors PrOfficialPoImportBatch's ROW_IDX_ / COL_IDX_
 * constants exactly (0-based row/col - see
 * phasep-gulliver/gulliver/src/main/java/jp/ne/glv/batch/PrOfficialPoImportBatch.java
 * and docs/official-po-integration-detailed-design.md 3章's Contract table).
 * {@link com.glv.gsysportal.service.excel.OfficialPoExcelGeneratorContractTest}
 * asserts every position independently against the same Contract.
 */
@Component
public class OfficialPoExcelGenerator {

    // Mirror of PrOfficialPoImportBatch's constants (0-based). Kept private
    // to this class - the Contract Test re-declares its own copy so the two
    // can never silently drift together undetected.
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

    public byte[] generate(OfficialPoExcelInput input) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("PO");
            CreationHelper createHelper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy/mm/dd"));

            set(sheet, ROW_PO_HEADER_1, COL_PO_HEADER_1, "PURCHASE ORDER");
            set(sheet, ROW_PO_HEADER_1_1, COL_PO_HEADER_1_1, "ORDER TO:");
            setDate(sheet, ROW_ORDR_DATE, COL_ORDR_DATE, input.orderDate(), dateStyle);
            set(sheet, ROW_PO_NO, COL_PO_NO, input.officialPoNo());
            set(sheet, ROW_BRAND_NAME, COL_BRAND_NAME, input.brandName());
            set(sheet, ROW_PO_HEADER_2, COL_PO_HEADER_2, "PURCHASER");

            set(sheet, ROW_DELIV, COL_DELIV_WEEK, input.deliveryWeek());
            set(sheet, ROW_DELIV, COL_DELIV_DATE, input.deliveryDate());
            set(sheet, ROW_DELIV, COL_SHIP_VIA, input.shipVia());
            set(sheet, ROW_DELIV, COL_SHIP_TERM, input.shipTerm());
            set(sheet, ROW_DELIV, COL_PAYMENT_TERM, input.paymentTerm());

            set(sheet, ROW_ITEM_HEADER, COL_NO, "No");
            set(sheet, ROW_ITEM_HEADER, COL_ITEM_CD, "Item No. On site");
            set(sheet, ROW_ITEM_HEADER, COL_SERIES, "Series");
            set(sheet, ROW_ITEM_HEADER, COL_MODEL_NO, "Model No.");
            set(sheet, ROW_ITEM_HEADER, COL_MODEL, "Model");
            set(sheet, ROW_ITEM_HEADER, COL_COLOR, "Color");
            set(sheet, ROW_ITEM_HEADER, COL_DESCRIPTION, "Description");
            set(sheet, ROW_ITEM_HEADER, COL_QTY_PO, "Order Qty");
            set(sheet, ROW_ITEM_HEADER, COL_PRC_UNIT, "Unit Price");
            set(sheet, ROW_ITEM_HEADER, COL_AMT_LINE, "Amount");
            set(sheet, ROW_ITEM_HEADER, COL_CTRY_ORG, "CntryOrg1");
            set(sheet, ROW_ITEM_HEADER, COL_BOX_HEIGHT, "Box height[cm]");
            set(sheet, ROW_ITEM_HEADER, COL_BOX_WIDTH, "Box width[cm]");
            set(sheet, ROW_ITEM_HEADER, COL_BOX_DEPTH, "Box depth[cm]");
            set(sheet, ROW_ITEM_HEADER, COL_WEIGHT, "Weight[kg]");

            List<OfficialPoExcelInput.Line> lines = input.lines();
            for (int i = 0; i < lines.size(); i++) {
                OfficialPoExcelInput.Line line = lines.get(i);
                int rowIdx = FIRST_ITEM_ROW + i;
                set(sheet, rowIdx, COL_NO, i + 1);
                set(sheet, rowIdx, COL_ITEM_CD, line.itemCode());
                set(sheet, rowIdx, COL_SERIES, line.series());
                set(sheet, rowIdx, COL_MODEL_NO, line.modelNo());
                set(sheet, rowIdx, COL_MODEL, line.model());
                set(sheet, rowIdx, COL_COLOR, line.color());
                set(sheet, rowIdx, COL_DESCRIPTION, line.description());
                set(sheet, rowIdx, COL_QTY_PO, line.qty());
                setCurrency(sheet, rowIdx, COL_PRC_UNIT, line.unitPrice(), line.currencySymbol(), wb, createHelper);
                setCurrency(sheet, rowIdx, COL_AMT_LINE,
                        line.unitPrice() == null ? null : line.unitPrice().multiply(BigDecimal.valueOf(line.qty())),
                        line.currencySymbol(), wb, createHelper);
                set(sheet, rowIdx, COL_CTRY_ORG, line.countryOfOrigin());
                setDecimal(sheet, rowIdx, COL_BOX_HEIGHT, line.boxHeight());
                setDecimal(sheet, rowIdx, COL_BOX_WIDTH, line.boxWidth());
                setDecimal(sheet, rowIdx, COL_BOX_DEPTH, line.boxDepth());
                setDecimal(sheet, rowIdx, COL_WEIGHT, line.weight());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate Official PO Excel", e);
        }
    }

    private static Cell cell(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            row = sheet.createRow(rowIdx);
        }
        return row.createCell(colIdx);
    }

    private static void set(Sheet sheet, int rowIdx, int colIdx, String value) {
        if (value == null) {
            return;
        }
        cell(sheet, rowIdx, colIdx).setCellValue(value);
    }

    private static void set(Sheet sheet, int rowIdx, int colIdx, int value) {
        cell(sheet, rowIdx, colIdx).setCellValue(value);
    }

    private static void setDecimal(Sheet sheet, int rowIdx, int colIdx, BigDecimal value) {
        if (value == null) {
            return;
        }
        cell(sheet, rowIdx, colIdx).setCellValue(value.doubleValue());
    }

    private static void setDate(Sheet sheet, int rowIdx, int colIdx, LocalDate value, CellStyle dateStyle) {
        if (value == null) {
            return;
        }
        Cell c = cell(sheet, rowIdx, colIdx);
        c.setCellValue(Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        c.setCellStyle(dateStyle);
    }

    /**
     * Currency is read by PrOfficialPoImportBatch.getCcyCode() from the
     * Cell's Number Format string, not its value
     * (docs/official-po-integration-detailed-design.md 3.4章) - so the
     * numeric value alone is never enough. Uses Excel's standard
     * {@code [$SYMBOL]#,##0.00} currency format, which embeds "$" + symbol
     * as a substring - exactly what getCcyCode()'s matcher looks for.
     */
    private static void setCurrency(Sheet sheet, int rowIdx, int colIdx, BigDecimal value, String currencySymbol,
                                     XSSFWorkbook wb, CreationHelper createHelper) {
        if (value == null) {
            return;
        }
        Cell c = cell(sheet, rowIdx, colIdx);
        c.setCellValue(value.doubleValue());
        if (currencySymbol != null && !currencySymbol.isBlank()) {
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(createHelper.createDataFormat().getFormat("[$" + currencySymbol + "]#,##0.00"));
            c.setCellStyle(style);
        }
    }
}
