package com.glv.gsysportal.service;

import com.glv.gsysportal.legacy.calc.OrderQuantityCalculator;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;

import java.util.List;

/**
 * Shared calc4 orchestration, extracted from the Step 0/1
 * {@code OrderCandidateService} so both the Candidate List and Create Draft
 * (Step 2) compute Recommended Qty identically, from the same
 * {@link LegacyStockRow}, via the verbatim-ported Legacy calc classes.
 */
public final class RecommendedQtyCalculator {

    // Same Default 4EU formula text as Legacy's StockCalculationHelper (private there);
    // duplicated here ONLY as literal fallback text, not a logic change.
    private static final String DEFAULT_FORMULA_11 =
        "IF([AT]<=5,(ROUNDUP([AT]*1-[AR]+[AT]*2/2,0)),IF([AT]<=10,(ROUNDUP([AT]*1-[AR]+[AT]*3/2,0)),ROUNDUP([AT]*1-[AR]+[AT]*4/2,0)))";
    private static final String DEFAULT_FORMULA_12 =
        "IF(ROUNDDOWN([AZ]-([AT]*2/2),0)<0,0,ROUNDDOWN([AZ]-([AT]*2/2),0))";
    private static final String DEFAULT_FORMULA_13 =
        "IF([AZ]-[BA]+(SUM([U]:[AO]))>[AT]*4/2,[AT]*4/2-(SUM([U]:[AO])),[AZ]-[BA])";
    private static final String DEFAULT_FORMULA_14 =
        "IF([BB]<=2,0,ROUNDDOWN([BB],0))";

    private RecommendedQtyCalculator() {
    }

    public static Integer calc4(LegacyStockRow row) {
        Integer logicalQty = sum(row.currentStock(), row.openPo(), row.openArrival(), row.openShip());
        Integer stkStandard = row.stkStandard();

        List<Integer> poQtyList = List.of(row.openPo() == null ? 0 : row.openPo());
        List<Integer> arrQtyList = List.of(row.openArrival() == null ? 0 : row.openArrival());

        Integer calc1 = OrderQuantityCalculator.calc1(logicalQty, stkStandard, row.formula11());
        Integer calc2 = OrderQuantityCalculator.calc2(calc1, stkStandard, row.formula12());
        Integer calc3 = OrderQuantityCalculator.calc3(calc1, calc2, stkStandard, poQtyList, arrQtyList, row.formula13());
        Integer calc4 = OrderQuantityCalculator.calc4(calc3, row.formula14());

        if (calc4 == null && isBlank(row.formula11()) && isBlank(row.formula12())
                && isBlank(row.formula13()) && isBlank(row.formula14())) {
            calc1 = OrderQuantityCalculator.calc1(logicalQty, stkStandard, DEFAULT_FORMULA_11);
            calc2 = OrderQuantityCalculator.calc2(calc1, stkStandard, DEFAULT_FORMULA_12);
            calc3 = OrderQuantityCalculator.calc3(calc1, calc2, stkStandard, poQtyList, arrQtyList, DEFAULT_FORMULA_13);
            calc4 = OrderQuantityCalculator.calc4(calc3, DEFAULT_FORMULA_14);
        }
        return calc4;
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
}
