package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public OrderCandidateService(LegacyStockReadRepository legacyStockReadRepository,
                                  RecommendedQtyCalculator recommendedQtyCalculator) {
        this.legacyStockReadRepository = legacyStockReadRepository;
        this.recommendedQtyCalculator = recommendedQtyCalculator;
    }

    public List<OrderCandidateResponse> findOrderCandidates(String brandCode, String supplierCode, String keyword) {
        List<LegacyStockRow> rows = legacyStockReadRepository.findOrderCandidates(brandCode, supplierCode, keyword);
        return rows.stream().map(this::toResponse).toList();
    }

    OrderCandidateResponse toResponse(LegacyStockRow row) {
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
                recommendedQtyCalculator.resolveRegion(row)
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
