package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.legacy.calc.OrderQuantityCalculator;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Order Candidate List - Legacy Read only, no Prototype DB writes.
 *
 * Recommended Qty is calc4, computed via the verbatim-ported Legacy calc logic
 * (com.glv.gsysportal.legacy.calc), never a fixed/seeded value (Technical Design
 * 4.3, implementation instructions 4章/7章).
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
    private static final String DATA_SOURCE_CODE = "DEMO_LEGACY";

    private final LegacyStockReadRepository legacyStockReadRepository;

    public OrderCandidateService(LegacyStockReadRepository legacyStockReadRepository) {
        this.legacyStockReadRepository = legacyStockReadRepository;
    }

    public List<OrderCandidateResponse> findOrderCandidates(String brandCode, String supplierCode, String keyword) {
        List<LegacyStockRow> rows = legacyStockReadRepository.findOrderCandidates(brandCode, supplierCode, keyword);
        return rows.stream().map(this::toResponse).toList();
    }

    private OrderCandidateResponse toResponse(LegacyStockRow row) {
        Integer logicalQty = sum(row.currentStock(), row.openPo(), row.openArrival(), row.openShip());
        Integer stkStandard = row.stkStandard();

        // calc3/calc3Alt sum lists internally (OrderQuantityCalculator.sumQuantities);
        // passing the pre-aggregated totals as single-element lists yields the same
        // result as passing the individual PO_QTY_1-20/ARR_QTY_1-10 slots, without
        // changing the calculation itself.
        List<Integer> poQtyList = List.of(row.openPo() == null ? 0 : row.openPo());
        List<Integer> arrQtyList = List.of(row.openArrival() == null ? 0 : row.openArrival());

        Integer calc1 = OrderQuantityCalculator.calc1(logicalQty, stkStandard, row.formula11());
        Integer calc2 = OrderQuantityCalculator.calc2(calc1, stkStandard, row.formula12());
        Integer calc3 = OrderQuantityCalculator.calc3(calc1, calc2, stkStandard, poQtyList, arrQtyList, row.formula13());
        Integer calc4 = OrderQuantityCalculator.calc4(calc3, row.formula14());

        // Formula未設定 -> Legacy Default 4EU fallback, applied the same way
        // StockCalculationHelper.calculateAllCalcs does when all 4 formulas are blank.
        if (calc4 == null && isBlank(row.formula11()) && isBlank(row.formula12())
                && isBlank(row.formula13()) && isBlank(row.formula14())) {
            calc1 = OrderQuantityCalculator.calc1(logicalQty, stkStandard, DEFAULT_FORMULA_11);
            calc2 = OrderQuantityCalculator.calc2(calc1, stkStandard, DEFAULT_FORMULA_12);
            calc3 = OrderQuantityCalculator.calc3(calc1, calc2, stkStandard, poQtyList, arrQtyList, DEFAULT_FORMULA_13);
            calc4 = OrderQuantityCalculator.calc4(calc3, DEFAULT_FORMULA_14);
        }

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
                DATA_SOURCE_CODE
        );
    }

    private static int sum(Integer... values) {
        int total = 0;
        for (Integer v : values) {
            total += (v == null ? 0 : v);
        }
        return total;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // Same Default 4EU formula text as Legacy's StockCalculationHelper (private there);
    // duplicated here ONLY as literal fallback text, not a logic change - the ported
    // OrderQuantityCalculator/FormulaParser classes still perform 100% of the parsing
    // and arithmetic. See legacy.calc.StockCalculationHelper for the origin of this text.
    private static final String DEFAULT_FORMULA_11 =
        "IF([AT]<=5,(ROUNDUP([AT]*1-[AR]+[AT]*2/2,0)),IF([AT]<=10,(ROUNDUP([AT]*1-[AR]+[AT]*3/2,0)),ROUNDUP([AT]*1-[AR]+[AT]*4/2,0)))";
    private static final String DEFAULT_FORMULA_12 =
        "IF(ROUNDDOWN([AZ]-([AT]*2/2),0)<0,0,ROUNDDOWN([AZ]-([AT]*2/2),0))";
    private static final String DEFAULT_FORMULA_13 =
        "IF([AZ]-[BA]+(SUM([U]:[AO]))>[AT]*4/2,[AT]*4/2-(SUM([U]:[AO])),[AZ]-[BA])";
    private static final String DEFAULT_FORMULA_14 =
        "IF([BB]<=2,0,ROUNDDOWN([BB],0))";
}
