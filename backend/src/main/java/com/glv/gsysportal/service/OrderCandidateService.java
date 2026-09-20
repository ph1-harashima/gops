package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.dto.response.SkuRestockExpectationResponse;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
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
