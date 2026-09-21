package com.glv.gsysportal.service;

import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
 * gops-stage4-targeted-real-data-remediation.md Remediation B, resolving
 * Stage 3B's confirmed Legacy formula: LOGICAL_QTY = PHISICAL_QTY + open_po
 * + open_ship - ARR_QTY is never a summand). Pure unit test, no DB - proves
 * the calc1 input this Strategy builds internally by comparing calc4's
 * output against a hand-built reference using OrderQuantityCalculator
 * directly, the same "compare against the real formula" idiom
 * RecommendedQtyCalculatorTest already established for the Strategy
 * dispatch layer above this one.
 */
class OverseasRecommendedQtyStrategyTest {

    private static final String FORMULA_11 = "IF([AT]<=5,(ROUNDUP([AT]*1-[AR]+[AT]*2/2,0)),IF([AT]<=10,(ROUNDUP([AT]*1-[AR]+[AT]*3/2,0)),ROUNDUP([AT]*1-[AR]+[AT]*4/2,0)))";
    private static final String FORMULA_12 = "IF(ROUNDDOWN([AZ]-([AT]*2/2),0)<0,0,ROUNDDOWN([AZ]-([AT]*2/2),0))";
    private static final String FORMULA_13 = "IF([AZ]-[BA]+(SUM([U]:[AO]))>[AT]*4/2,[AT]*4/2-(SUM([U]:[AO])),[AZ]-[BA])";
    private static final String FORMULA_14 = "IF([BB]<=2,0,ROUNDDOWN([BB],0))";

    private static LegacyStockRow row(Integer currentStock, Integer stkStandard, Integer openPo, Integer openArrival, Integer openShip) {
        return new LegacyStockRow(
                "SKU-1", "Item One", "BR_OUTDOOR", "Brand One", "30", "NEW", false,
                currentStock, stkStandard, 20, openPo, openArrival, openShip,
                FORMULA_11, FORMULA_12, FORMULA_13, FORMULA_14,
                "SUP_ALPHA", "Supplier One", BigDecimal.TEN, "JPY", LocalDateTime.now());
    }

    /**
     * The decisive proof: two rows differing ONLY in openArrival (0 vs.
     * 100 - large enough that any leakage into the calc would be
     * impossible to miss) must produce the IDENTICAL Recommended Qty.
     * Before Remediation B, this test would fail (openArrival was summed
     * into logicalQty).
     */
    @Test
    void arrQtyNeverAffectsRecommendedQty() {
        LegacyStockRow withoutArrival = row(35, 8, 3, 0, 7);
        LegacyStockRow withHugeArrival = row(35, 8, 3, 100, 7);

        Integer resultWithoutArrival = new OverseasRecommendedQtyStrategy().calculate(withoutArrival);
        Integer resultWithHugeArrival = new OverseasRecommendedQtyStrategy().calculate(withHugeArrival);

        assertEquals(resultWithoutArrival, resultWithHugeArrival,
                "LOGICAL_QTY = PHISICAL_QTY + open_po + open_ship only (Stage 3B §5) - "
                        + "ARR_QTY must never change the Recommended Qty result");
    }

    /**
     * Positive proof of the correct composite: logicalQty =
     * currentStock(=PHISICAL_QTY) + openPo + openShip, verified by
     * reproducing calc1 directly via OrderQuantityCalculator with that
     * exact sum and comparing to the Strategy's own calc4 chain output.
     */
    @Test
    void recommendedQtyReceivesPhysicalQtyPlusOpenPoPlusOpenShip() {
        LegacyStockRow row = row(35, 8, 3, 999, 7); // openArrival deliberately large/irrelevant
        int expectedLogicalQty = 35 + 3 + 7; // = 45, NOT 35+3+999+7

        Integer expectedCalc1 = com.glv.gsysportal.legacy.calc.OrderQuantityCalculator.calc1(expectedLogicalQty, 8, FORMULA_11);
        Integer expectedCalc2 = com.glv.gsysportal.legacy.calc.OrderQuantityCalculator.calc2(expectedCalc1, 8, FORMULA_12);
        Integer expectedCalc3 = com.glv.gsysportal.legacy.calc.OrderQuantityCalculator.calc3(
                expectedCalc1, expectedCalc2, 8, java.util.List.of(3), java.util.List.of(999), FORMULA_13);
        Integer expectedCalc4 = com.glv.gsysportal.legacy.calc.OrderQuantityCalculator.calc4(expectedCalc3, FORMULA_14);

        assertEquals(expectedCalc4, new OverseasRecommendedQtyStrategy().calculate(row));
    }
}
