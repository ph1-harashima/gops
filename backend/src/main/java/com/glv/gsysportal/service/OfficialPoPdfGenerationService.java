package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.service.excel.OfficialPoExcelInput;
import com.glv.gsysportal.service.excel.OfficialPoPdfGenerator;
import com.glv.gsysportal.service.excel.OfficialPoPdfInput;
import com.glv.gsysportal.service.excel.OfficialPoPdfStorageService;
import org.springframework.stereotype.Service;

/**
 * Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
 * assembles the "G-OPS Standard Official PO PDF" from the SAME
 * {@link OfficialPoExcelInput} {@link OfficialPoExcelGenerationService}
 * builds for the Excel artifact (via its {@code buildInput}), so Excel and
 * PDF can never disagree about Qty/PO info. Mirrors
 * {@code OfficialPoExcelGenerationService}'s own shape exactly (same
 * generate-then-store split) - kept as a separate class since the PDF
 * artifact has its own storage slot and never touches the Integration
 * Status state machine.
 */
@Service
public class OfficialPoPdfGenerationService {

    private final OfficialPoExcelGenerationService excelGenerationService;
    private final OfficialPoPdfGenerator generator;
    private final OfficialPoPdfStorageService storageService;

    public OfficialPoPdfGenerationService(OfficialPoExcelGenerationService excelGenerationService,
                                           OfficialPoPdfGenerator generator,
                                           OfficialPoPdfStorageService storageService) {
        this.excelGenerationService = excelGenerationService;
        this.generator = generator;
        this.storageService = storageService;
    }

    /** Builds the PDF, stores it, and returns the fileKey to persist on the
     * Integration Request. Never writes to Legacy or the Import Folder - the
     * PDF is a Portal-only reference artifact. */
    public String generateAndStore(PortalOrder order, OfficialPoIntegrationRequest request) {
        OfficialPoExcelInput data = excelGenerationService.buildInput(order, request);
        OfficialPoPdfInput input = new OfficialPoPdfInput(data, order.getSupplierNameSnapshot(), request.getRevisionNo());
        byte[] bytes = generator.generate(input);
        return storageService.store(order.getId(), request.getRevisionNo(), bytes);
    }

    public byte[] load(String fileKey) {
        return storageService.load(fileKey);
    }
}
