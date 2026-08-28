package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.SkuDetailResponse;
import com.glv.gsysportal.dto.response.SkuPoHistoryLine;
import com.glv.gsysportal.exception.SkuNotFoundException;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyPoHistoryRow;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * SKU Detail (implementation instructions Step 5 4章) - Legacy Read only,
 * no Prototype DB writes, same READ ONLY guarantee as Order Candidate List
 * (Technical Design 4.1).
 */
@Service
public class SkuDetailService {

    private final LegacyStockReadRepository legacyStockReadRepository;

    public SkuDetailService(LegacyStockReadRepository legacyStockReadRepository) {
        this.legacyStockReadRepository = legacyStockReadRepository;
    }

    public SkuDetailResponse getDetail(String sku) {
        List<LegacyStockRow> rows = legacyStockReadRepository.findBySkus(Set.of(sku));
        if (rows.isEmpty()) {
            throw new SkuNotFoundException(Set.of(sku));
        }
        LegacyStockRow row = rows.get(0);
        Integer calc4 = RecommendedQtyCalculator.calc4(row);

        List<SkuPoHistoryLine> history = legacyStockReadRepository.findPoHistoryBySku(sku).stream()
                .map(SkuDetailService::toHistoryLine)
                .toList();

        return new SkuDetailResponse(
                row.itemCd(), row.itemName(), row.brandCd(), row.brandName(),
                row.supplierCd(), row.supplierName(), row.itemStatus(),
                row.currentStock(), null /* Safety Stock: not in this Step's reduced Read Query scope, same as Order Candidate List */,
                row.openPo(), row.openArrival(),
                row.monthlySales(), row.leadTime(), calc4, row.unitPrice(), row.currency(),
                OrderCandidateService.DATA_SOURCE_CODE,
                history
        );
    }

    private static SkuPoHistoryLine toHistoryLine(LegacyPoHistoryRow r) {
        return new SkuPoHistoryLine(r.poNo(), r.orderDate(), r.status(), r.qty(), r.unitPrice(), r.currency(),
                r.supplierCd(), r.supplierName());
    }
}
