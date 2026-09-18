package com.glv.gsysportal.service.excel;

/**
 * Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章): wraps
 * the SAME {@link OfficialPoExcelInput} the Excel Generator consumes
 * (identical PO No./Supplier/Brand/dates/line Qty/Unit Price - assembled once
 * by {@code OfficialPoExcelGenerationService#buildInput}, never a second,
 * independently-maintained data path) plus the two fields the PDF shows that
 * the Excel Contract has no cell for (Supplier Name, Revision No.) - both
 * read from the same {@code PortalOrder}/{@code OfficialPoIntegrationRequest}
 * the Excel path already uses, so Excel and PDF can never disagree about
 * quantities or PO identity (docs 7章's explicit design requirement).
 */
public record OfficialPoPdfInput(
        OfficialPoExcelInput data,
        String supplierName,
        int revisionNo
) {
}
