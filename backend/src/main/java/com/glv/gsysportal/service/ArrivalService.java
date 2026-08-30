package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.ArrivalDetailResponse;
import com.glv.gsysportal.dto.response.ArrivalLineView;
import com.glv.gsysportal.dto.response.ArrivalSummaryResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.exception.ArrivalNotFoundException;
import com.glv.gsysportal.repository.legacy.ArrivalReadRepository;
import com.glv.gsysportal.repository.legacy.ArrivalReadRepository.ArrivalListFilter;
import com.glv.gsysportal.repository.legacy.FulfillmentReadRepository;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyArrivalHeaderRow;
import com.glv.gsysportal.repository.legacy.row.LegacyInvoiceLineRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoLineRow;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Arrival Visibility Foundation (Phase 8-G 4章/5章/6章). Pure Legacy READ
 * ONLY - no Portal DB table, no Business Transaction, no Workflow State
 * (13章). {@code page}/{@code size} are clamped the same way in both this
 * class and {@link WarehouseStockService} - see {@link #clampSize}.
 */
@Service
public class ArrivalService {

    /** Phase 8-G 5章/9章: a hard ceiling so an unbounded ?size= query param
     * can never force an unpaginated fetch-all against Legacy. */
    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    private final ArrivalReadRepository arrivalReadRepository;
    private final FulfillmentReadRepository fulfillmentReadRepository;
    private final LegacyStockReadRepository legacyStockReadRepository;

    public ArrivalService(ArrivalReadRepository arrivalReadRepository, FulfillmentReadRepository fulfillmentReadRepository,
                           LegacyStockReadRepository legacyStockReadRepository) {
        this.arrivalReadRepository = arrivalReadRepository;
        this.fulfillmentReadRepository = fulfillmentReadRepository;
        this.legacyStockReadRepository = legacyStockReadRepository;
    }

    public PageResponse<ArrivalSummaryResponse> list(String supplierCode, String brandCode, String poNumber,
                                                       String invoiceNumber, String skuKeyword,
                                                       LocalDate arrivalDateFrom, LocalDate arrivalDateTo,
                                                       Integer page, Integer size) {
        ArrivalListFilter filter = new ArrivalListFilter(
                blankToNull(supplierCode), blankToNull(brandCode), blankToNull(poNumber),
                blankToNull(invoiceNumber), blankToNull(skuKeyword), arrivalDateFrom, arrivalDateTo);
        int clampedSize = clampSize(size);
        int clampedPage = page == null || page < 0 ? 0 : page;
        int offset = clampedPage * clampedSize;

        long total = arrivalReadRepository.countList(filter);
        List<ArrivalSummaryResponse> content = arrivalReadRepository.findList(filter, clampedSize, offset).stream()
                .map(ArrivalService::toSummary)
                .toList();
        return PageResponse.of(content, clampedPage, clampedSize, total);
    }

    public ArrivalDetailResponse detail(String supplierCode, String poNumber, String invoiceNumber) {
        LegacyArrivalHeaderRow header = arrivalReadRepository.findOne(supplierCode, poNumber, invoiceNumber)
                .orElseThrow(() -> new ArrivalNotFoundException(supplierCode, poNumber, invoiceNumber));

        // Reuses FulfillmentReadRepository's own PO/Invoice line queries -
        // the exact same Original+Credit netting Phase 7-C7A already
        // established for qty_stk_in (Phase 8-G 6章's "no re-derived
        // Business Logic" principle; see ArrivalLineView's own Javadoc).
        List<LegacyPoLineRow> poLines = fulfillmentReadRepository.findPoLines(poNumber);
        List<LegacyInvoiceLineRow> invoiceLines = fulfillmentReadRepository.findInvoiceLines(supplierCode, poNumber);

        Set<String> skus = poLines.stream().map(LegacyPoLineRow::itemCd).collect(Collectors.toSet());
        Map<String, String> itemNameBySku = new LinkedHashMap<>();
        if (!skus.isEmpty()) {
            for (LegacyStockRow row : legacyStockReadRepository.findBySkus(skus)) {
                itemNameBySku.put(row.itemCd(), row.itemName());
            }
        }

        List<ArrivalLineView> lines = poLines.stream()
                .map(poLine -> toLineView(poLine, invoiceLines, poNumber, itemNameBySku))
                .toList();

        return new ArrivalDetailResponse(toSummary(header), lines);
    }

    private static ArrivalLineView toLineView(LegacyPoLineRow poLine, List<LegacyInvoiceLineRow> invoiceLines,
                                               String poNumber, Map<String, String> itemNameBySku) {
        Integer orderedQty = poLine.qtyPo();
        int invoiceQty = 0;
        int stockInQty = 0;
        boolean anyInvoiceQty = false;
        boolean anyStockInQty = false;
        for (LegacyInvoiceLineRow line : invoiceLines) {
            if (!poLine.itemCd().equals(line.itemCd())) {
                continue;
            }
            // invoiceQty: THIS Arrival's own (Original) Invoice line only -
            // never a linked Credit line's qty (FulfillmentReadRepository's
            // Javadoc explains why: a Credit line's qty mirrors a
            // discrepancy adjustment, not a fresh invoiced amount).
            boolean isOriginalPoLine = poNumber.equals(line.poNo());
            if (isOriginalPoLine && line.qty() != null) {
                invoiceQty += line.qty();
                anyInvoiceQty = true;
            }
            // stockInQty: Original AND any linked Credit row together - the
            // TRUE physical stock-in qty (same netting as Fulfillment).
            if (line.qtyStkIn() != null) {
                stockInQty += line.qtyStkIn();
                anyStockInQty = true;
            }
        }
        String itemName = itemNameBySku.getOrDefault(poLine.itemCd(), poLine.itemCd());
        return new ArrivalLineView(poLine.itemCd(), itemName, orderedQty,
                anyInvoiceQty ? invoiceQty : null, anyStockInQty ? stockInQty : null);
    }

    private static ArrivalSummaryResponse toSummary(LegacyArrivalHeaderRow row) {
        return new ArrivalSummaryResponse(
                row.supplierCd(), row.supplierName(), row.poNo(), row.invNo(),
                row.brandCd(), row.brandName(), row.blNo(), row.vesselNo(),
                row.orderedQty(), row.invoiceQty(), row.qty(), row.stockInQty(),
                row.etd(), row.eta(), row.etaWh(), row.stkInDate(),
                row.whRepStatus(), row.whRepResult());
    }

    static int clampSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
