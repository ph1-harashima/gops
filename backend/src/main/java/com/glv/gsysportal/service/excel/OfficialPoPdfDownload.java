package com.glv.gsysportal.service.excel;

/** Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
 * mirrors {@link OfficialPoExcelDownload} for the PDF artifact. */
public record OfficialPoPdfDownload(byte[] bytes, String fileName) {
}
