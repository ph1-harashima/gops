package com.glv.gsysportal.service.excel;

/** Gap Analysis B-3 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
 * pairs the stored Excel bytes with a human-identifiable file name computed
 * via {@link OfficialPoFileNaming} - the Controller no longer invents its
 * own ad hoc "official-po-{id}.xlsx" name. */
public record OfficialPoExcelDownload(byte[] bytes, String fileName) {
}
