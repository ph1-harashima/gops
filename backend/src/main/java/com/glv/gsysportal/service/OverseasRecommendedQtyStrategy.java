package com.glv.gsysportal.service;

import com.glv.gsysportal.legacy.calc.OrderQuantityCalculator;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;

import java.util.List;

/** BR-09: "OVERSEAS → 現行Legacy計算方式" - the exact calc1-4 orchestration
 * this codebase already ran unconditionally before this Strategy split
 * existed (verbatim, not a rewrite - see git history of
 * {@code RecommendedQtyCalculator} for the pre-Strategy version this was
 * extracted from unchanged). */
public class OverseasRecommendedQtyStrategy implements RecommendedQtyStrategy {

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

    @Override
    public Integer calculate(LegacyStockRow row) {
        // Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
        // gops-stage3b-legacy-stock-logic-audit.md §5, confirmed via Legacy
        // source): LOGICAL_QTY = PHISICAL_QTY + open_po + open_ship only -
        // ARR_QTY is never a LOGICAL_QTY summand in Legacy's own SQL.
        // row.currentStock() is PHISICAL_QTY (RecommendedQtyReadQuery.sql) -
        // the display figure and this calc input are the same value by
        // design, not a coincidence; row.openArrival() itself remains a
        // separate, correctly-sourced field used only for direct display
        // elsewhere, never folded into this calc.
        Integer logicalQty = sum(row.currentStock(), row.openPo(), row.openShip());
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
