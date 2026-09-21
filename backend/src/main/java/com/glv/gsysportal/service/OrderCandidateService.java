package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.SkuRestockExpectationResponse;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository.StockSalesListFilter;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Order Candidate List - Legacy Read only, no Prototype DB writes.
 *
 * Recommended Qty is calc4, computed via the verbatim-ported Legacy calc logic
 * (com.glv.gsysportal.legacy.calc) through {@link RecommendedQtyCalculator},
 * never a fixed/seeded value (Technical Design 4.3, implementation
 * instructions 4章/7章).
 */
@Service
public class OrderCandidateService {

    // Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
    // gops-stage4-targeted-real-data-remediation.md): matches
    // StockSalesService's own MAX/DEFAULT_PAGE_SIZE exactly - one shared
    // Candidate List / Stock-Sales List pagination contract, not two
    // independently-chosen ones.
    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * All data in this Step is sourced from the Legacy Demo Instance
     * (see docker-compose.yml legacy-demo-mysql) rather than a shared/real
     * Legacy G-SYS database - surfaced to the Frontend so it is never confused
     * with production data (implementation instructions 0章/7章 data source
     * identification requirement).
     */
    public static final String DATA_SOURCE_CODE = "DEMO_LEGACY";

    private final LegacyStockReadRepository legacyStockReadRepository;
    private final RecommendedQtyCalculator recommendedQtyCalculator;
    private final SkuRestockExpectationService restockExpectationService;

    public OrderCandidateService(LegacyStockReadRepository legacyStockReadRepository,
                                  RecommendedQtyCalculator recommendedQtyCalculator,
                                  SkuRestockExpectationService restockExpectationService) {
        this.legacyStockReadRepository = legacyStockReadRepository;
        this.recommendedQtyCalculator = recommendedQtyCalculator;
        this.restockExpectationService = restockExpectationService;
    }

    public List<OrderCandidateResponse> findOrderCandidates(String brandCode, String supplierCode, String keyword) {
        List<LegacyStockRow> rows = legacyStockReadRepository.findOrderCandidates(brandCode, supplierCode, keyword);
        // Post-Freeze Business Refinement: one bulk restock-expectation
        // lookup for the whole List, not one per row (same "no N+1"
        // discipline the rest of this List already follows).
        Map<String, SkuRestockExpectationResponse> restockBySku =
                restockExpectationService.getBulk(rows.stream().map(LegacyStockRow::itemCd).toList());
        return rows.stream().map(row -> toResponse(row, restockBySku.get(row.itemCd()))).toList();
    }

    /**
     * Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
     * gops-stage3-real-data-compatibility-review.md Finding #3 - a
     * confirmed real Brand has 18,596 SKUs and this List had no pagination
     * at all). Backend-paginated - {@link #findOrderCandidates} itself is
     * deliberately left unchanged, since {@code DashboardService} and
     * {@code SupplierMasterService} both call it expecting the complete,
     * unpaginated result for their own aggregate/KPI computation, not a
     * single page. Reuses {@link LegacyStockReadRepository#findStockSalesList}/
     * {@link LegacyStockReadRepository#countStockSalesList} - the exact
     * SQL/pagination contract {@code StockSalesService} already uses
     * (same base query, same default/max page size), not a second,
     * independently-maintained pagination implementation.
     */
    public PageResponse<OrderCandidateResponse> findOrderCandidatesPage(String brandCode, String supplierCode, String keyword,
                                                                          Integer page, Integer size) {
        StockSalesListFilter filter = new StockSalesListFilter(
                blankToNull(keyword), blankToNull(brandCode), blankToNull(supplierCode),
                null, null, null, null);
        int clampedSize = clampSize(size);
        int clampedPage = page == null || page < 0 ? 0 : page;
        int offset = clampedPage * clampedSize;

        long total = legacyStockReadRepository.countStockSalesList(filter);
        List<LegacyStockRow> rows = legacyStockReadRepository.findStockSalesList(filter, clampedSize, offset);
        Map<String, SkuRestockExpectationResponse> restockBySku =
                restockExpectationService.getBulk(rows.stream().map(LegacyStockRow::itemCd).toList());
        List<OrderCandidateResponse> content = rows.stream()
                .map(row -> toResponse(row, restockBySku.get(row.itemCd())))
                .toList();
        return PageResponse.of(content, clampedPage, clampedSize, total);
    }

    private static int clampSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    OrderCandidateResponse toResponse(LegacyStockRow row, SkuRestockExpectationResponse restock) {
        Integer calc4 = recommendedQtyCalculator.calc4(row);
        return new OrderCandidateResponse(
                row.itemCd(),
                row.itemName(),
                row.brandCd(),
                row.brandName(),
                row.supplierCd(),
                row.supplierName(),
                row.currentStock(),
                null, // safetyStock: Legacy computes this dynamically via Formula.getSafetyStk(soldQty),
                      // not part of this Step's reduced Read Query scope - left null rather than guessed.
                sum(row.openPo(), row.openArrival()),
                row.monthlySales(),
                row.leadTime(),
                calc4,
                row.itemStatus(),
                row.discon(),
                row.unitPrice(),
                row.currency(),
                DATA_SOURCE_CODE,
                recommendedQtyCalculator.resolveRegion(row),
                restock == null ? SkuRestockExpectationResponse.SOURCE_NONE : restock.source(),
                restock == null ? null : restock.date(),
                restock == null ? null : restock.stockoutStatus(),
                restock == null ? null : restock.informationReceivedDate(),
                restock == null ? null : restock.contactMethod(),
                restock != null && restock.hasConflict()
        );
    }

    private static int sum(Integer... values) {
        int total = 0;
        for (Integer v : values) {
            total += (v == null ? 0 : v);
        }
        return total;
    }
}
