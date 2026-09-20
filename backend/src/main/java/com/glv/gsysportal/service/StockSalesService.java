package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.SkuRestockExpectationResponse;
import com.glv.gsysportal.dto.response.StockSalesSummaryResponse;
import com.glv.gsysportal.exception.SkuNotFoundException;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository.StockSalesListFilter;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stock/Sales Visibility Foundation (Phase 8-H 4章/7章). Pure Legacy READ
 * ONLY, reusing {@link LegacyStockReadRepository}'s existing query and
 * {@link RecommendedQtyCalculator}'s existing calc4 - no new calculation
 * logic anywhere in this class (8章/9章). No Portal DB table, no Business
 * Transaction, no History Persistence (14章/15章).
 */
@Service
public class StockSalesService {

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    private final LegacyStockReadRepository legacyStockReadRepository;
    private final RecommendedQtyCalculator recommendedQtyCalculator;
    private final SkuRestockExpectationService restockExpectationService;

    public StockSalesService(LegacyStockReadRepository legacyStockReadRepository,
                              RecommendedQtyCalculator recommendedQtyCalculator,
                              SkuRestockExpectationService restockExpectationService) {
        this.legacyStockReadRepository = legacyStockReadRepository;
        this.recommendedQtyCalculator = recommendedQtyCalculator;
        this.restockExpectationService = restockExpectationService;
    }

    public PageResponse<StockSalesSummaryResponse> list(String skuKeyword, String brandCode, String supplierCode,
                                                          Integer minStock, Integer maxStock, Integer minSales, Integer maxSales,
                                                          Integer page, Integer size) {
        StockSalesListFilter filter = new StockSalesListFilter(
                blankToNull(skuKeyword), blankToNull(brandCode), blankToNull(supplierCode),
                minStock, maxStock, minSales, maxSales);
        int clampedSize = clampSize(size);
        int clampedPage = page == null || page < 0 ? 0 : page;
        int offset = clampedPage * clampedSize;

        long total = legacyStockReadRepository.countStockSalesList(filter);
        List<LegacyStockRow> rows = legacyStockReadRepository.findStockSalesList(filter, clampedSize, offset);
        // Post-Freeze Business Refinement: one bulk lookup for the current
        // page only (this List is already Backend-paginated - never a
        // fetch-all), same no-N+1 pattern as Candidate List/Order History.
        Map<String, SkuRestockExpectationResponse> restockBySku =
                restockExpectationService.getBulk(rows.stream().map(LegacyStockRow::itemCd).toList());
        List<StockSalesSummaryResponse> content = rows.stream()
                .map(row -> toSummary(row, restockBySku.get(row.itemCd())))
                .toList();
        return PageResponse.of(content, clampedPage, clampedSize, total);
    }

    /** Reuses {@link LegacyStockReadRepository#findBySkus} unchanged - the
     * SAME Backend re-fetch Create Draft already relies on (Phase 8-H 8章). */
    public StockSalesSummaryResponse detail(String sku) {
        List<LegacyStockRow> rows = legacyStockReadRepository.findBySkus(Set.of(sku));
        if (rows.isEmpty()) {
            throw new SkuNotFoundException(Set.of(sku));
        }
        return toSummary(rows.get(0), restockExpectationService.get(sku));
    }

    private StockSalesSummaryResponse toSummary(LegacyStockRow row, SkuRestockExpectationResponse restock) {
        Integer recommendedQty = recommendedQtyCalculator.calc4(row);
        return new StockSalesSummaryResponse(
                row.itemCd(), row.itemName(), row.brandCd(), row.brandName(),
                row.supplierCd(), row.supplierName(),
                row.currentStock(), row.monthlySales(), row.openPo(), row.openArrival(),
                recommendedQty, row.leadTime(), row.itemStatus(), row.updateDatetime(),
                restock == null ? SkuRestockExpectationResponse.SOURCE_NONE : restock.source(),
                restock == null ? null : restock.date(),
                restock == null ? null : restock.stockoutStatus(),
                restock == null ? null : restock.informationReceivedDate(),
                restock == null ? null : restock.contactMethod(),
                restock != null && restock.hasConflict());
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
}
