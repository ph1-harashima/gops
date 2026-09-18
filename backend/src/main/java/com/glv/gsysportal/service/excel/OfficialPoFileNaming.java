package com.glv.gsysportal.service.excel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Gap Analysis B-3/B-4 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
 * human-identifiable Official PO artifact file naming.
 *
 * <p><b>WORKING ASSUMPTION</b> (not a confirmed Gulliver requirement - the
 * actual Import Folder identification convention is unconfirmed, 7章's
 * Unknown #(b)): {@code OfficialPO_{Supplier}_{Brand}_{yyyyMMdd}_{PONo}_{Revision3digit}.{ext}}.
 *
 * <p><b>FACT</b> (re-confirmed this Phase, `phasep-gulliver/gulliver/src/main/java/jp/ne/glv/batch/PrOfficialPoImportBatch.java`):
 * the Legacy Import Batch reads PO No./dates/etc. only from Cell content
 * (ROW_IDX_/COL_IDX_ constants) - the file name string is used only as an
 * error-message label ({@code addErrorFile}/{@code addErrorCell}), never
 * parsed for any business value. Changing this format therefore carries no
 * Import Contract risk.
 *
 * <p>"Company" is deliberately NOT included - its definition is unconfirmed
 * (docs/gulliver-20260917-phase1-gap-analysis.md 14章 TODO). Supplier/Brand
 * already identify the artifact per the existing Data Model.
 */
public final class OfficialPoFileNaming {

    private static final DateTimeFormatter DATE_SEGMENT = DateTimeFormatter.ofPattern("yyyyMMdd");
    // Characters unsafe in a file name on Windows/macOS/Linux, plus
    // whitespace - Legacy Codes (Supplier/Brand) and a staff-entered PO No.
    // must never be trusted to already be filename-safe.
    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[\\\\/:*?\"<>|\\s]+");

    private OfficialPoFileNaming() {
    }

    /** Builds a human-identifiable file name shared by every Official PO
     * artifact (Excel download, Excel Import Folder placement, and - once
     * implemented - the PDF equivalent) so the same Order/Revision always
     * produces the same recognizable name regardless of artifact type. */
    public static String buildFileName(String supplierCode, String brandCode, LocalDate date,
                                        String officialPoNo, int revisionNo, String extension) {
        String supplier = sanitize(supplierCode, "SUPPLIER");
        String brand = sanitize(brandCode, "BRAND");
        String dateSegment = (date == null ? LocalDate.now() : date).format(DATE_SEGMENT);
        String poNo = sanitize(officialPoNo, "UNASSIGNED");
        String revisionSegment = String.format("%03d", Math.max(revisionNo, 0));
        return "OfficialPO_" + supplier + "_" + brand + "_" + dateSegment + "_" + poNo + "_" + revisionSegment + "." + extension;
    }

    private static String sanitize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String cleaned = UNSAFE_CHARACTERS.matcher(raw.trim()).replaceAll("_");
        return cleaned.isBlank() ? fallback : cleaned;
    }
}
