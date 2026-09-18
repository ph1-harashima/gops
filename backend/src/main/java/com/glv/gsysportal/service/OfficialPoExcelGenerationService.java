package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.repository.legacy.OfficialPoPreflightReadRepository;
import com.glv.gsysportal.service.excel.OfficialPoExcelGenerator;
import com.glv.gsysportal.service.excel.OfficialPoExcelInput;
import com.glv.gsysportal.service.excel.OfficialPoExcelStorageService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Phase 9-A: assembles {@link OfficialPoExcelInput} from real Portal Order
 * data (the first real caller of {@link OfficialPoExcelGenerator} - every
 * prior Phase only exercised it from Contract Tests) and stores the
 * resulting bytes. Kept separate from {@link OfficialPoIntegrationService}
 * (which owns the Integration Request state machine) so the "how do I turn
 * an Order into Excel Input" concern doesn't bloat that class, mirroring the
 * existing {@code OfficialPoPreflightService} split.
 *
 * <p>{@code brandName} is resolved live from Legacy
 * ({@link OfficialPoPreflightReadRepository#findBrandName}) rather than
 * reusing {@code portal_order.brand_name_snapshot} - the Legacy Import Batch
 * requires an exact (case-insensitive) match against the CURRENT MS_COMM
 * Brand Master name (design doc §3.2), and the Portal snapshot can drift
 * from that over time.
 *
 * <p>Series/Model No./Model/Color/Country of Origin/Box dimensions/Weight
 * have no home in {@link PortalOrderDetail} today (Legacy Item Master
 * attributes, not per-Order-line data Portal captures) - left null, which
 * the Generator already treats as "skip this optional cell" and Legacy
 * treats as "no MsItem update from this row" (design doc §3.3: all optional).
 * {@code description} is populated from {@code itemNameSnapshot} - the
 * closest thing Portal already has, better than leaving it blank.
 */
@Service
public class OfficialPoExcelGenerationService {

    private final OfficialPoPreflightReadRepository preflightReadRepository;
    private final OfficialPoExcelGenerator generator;
    private final OfficialPoExcelStorageService storageService;

    public OfficialPoExcelGenerationService(OfficialPoPreflightReadRepository preflightReadRepository,
                                             OfficialPoExcelGenerator generator,
                                             OfficialPoExcelStorageService storageService) {
        this.preflightReadRepository = preflightReadRepository;
        this.generator = generator;
        this.storageService = storageService;
    }

    /** Builds the Excel, stores it, and returns the fileKey to persist on
     * the Integration Request. Never writes to Legacy or the Import Folder
     * (Phase 9-B's responsibility). */
    public String generateAndStore(PortalOrder order, OfficialPoIntegrationRequest request) {
        OfficialPoExcelInput input = buildInput(order, request);
        byte[] bytes = generator.generate(input);
        return storageService.store(order.getId(), request.getRevisionNo(), bytes);
    }

    /** Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
     * extracted so {@code OfficialPoPdfGenerationService} assembles the PDF
     * from this EXACT SAME {@link OfficialPoExcelInput} - one Business Data
     * Source for both artifacts, never two independently-maintained
     * assembly paths that could silently disagree on Qty/PO info. */
    public OfficialPoExcelInput buildInput(PortalOrder order, OfficialPoIntegrationRequest request) {
        String brandName = preflightReadRepository.findBrandName(order.getBrandCode());
        List<OfficialPoExcelInput.Line> lines = order.getDetails().stream()
                .filter(d -> !d.isRemoved())
                .map(d -> toLine(d, order.getCurrency()))
                .toList();

        return new OfficialPoExcelInput(
                request.getOfficialPoNo(),
                order.getSupplierCode(),
                order.getBrandCode(),
                brandName,
                order.getOrderDate(),
                request.getDeliveryWeek(),
                request.getDeliveryDate(),
                request.getShipVia(),
                request.getShipTerm(),
                request.getPaymentTerm(),
                lines
        );
    }

    public byte[] load(String fileKey) {
        return storageService.load(fileKey);
    }

    private OfficialPoExcelInput.Line toLine(PortalOrderDetail detail, String currencySymbol) {
        return new OfficialPoExcelInput.Line(
                detail.getSku(),
                null,
                null,
                null,
                null,
                detail.getItemNameSnapshot(),
                detail.getOrderQty(),
                detail.getUnitPrice(),
                currencySymbol,
                null,
                null,
                null,
                null,
                null
        );
    }
}
