package com.glv.gsysportal.legacy.calc;

import org.json.simple.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Golden Test for the verbatim-ported Legacy calc4 logic
 * (OrderQuantityCalculator / StockCalculationHelper / FormulaParser).
 *
 * IMPORTANT CORRECTION vs. Technical Design 13 章:
 * The original plan was to reuse Legacy's existing
 * jp.ne.glv.utilities.FormulaTest.java as the Golden Test baseline.
 * On inspection (this implementation session), that file is found to be
 * ENTIRELY COMMENTED OUT and tests a *different* class (Formula.java's
 * PRC_LIST/PRC_SELL price formulas), not OrderQuantityCalculator/calc4 at all.
 * Legacy has NO pre-existing automated test for calc4.
 *
 * Therefore, the expected values below were derived independently:
 *   1. By hand, directly from the Default formula text
 *      (DEFAULT_FORMULA_11_4EU .. DEFAULT_FORMULA_14_4EU, as ported in
 *      StockCalculationHelper) and the exact rounding semantics documented
 *      in OrderQuantityCalculator's Javadoc (ROUNDUP = away-from-zero,
 *      ROUNDDOWN = toward-zero).
 *   2. Cross-checked with an independent Python re-implementation of the
 *      same formulas (not sharing any code with the Java classes under test).
 * Both derivations agreed exactly for every case below.
 *
 * This test therefore validates "the ported Java code reproduces the
 * documented Legacy Default-formula behavior", not "the ported code matches
 * a pre-existing Legacy test suite" (none exists). See final implementation
 * report for this correction.
 */
class OrderQuantityCalculatorGoldenTest {

    // Legacy Default formula text, copied from StockCalculationHelper (package-private constants
    // are re-declared here only because the originals are private; the STRING VALUES are identical).
    private static final String FORMULA_11 =
        "IF([AT]<=5,(ROUNDUP([AT]*1-[AR]+[AT]*2/2,0)),IF([AT]<=10,(ROUNDUP([AT]*1-[AR]+[AT]*3/2,0)),ROUNDUP([AT]*1-[AR]+[AT]*4/2,0)))";
    private static final String FORMULA_12 =
        "IF(ROUNDDOWN([AZ]-([AT]*2/2),0)<0,0,ROUNDDOWN([AZ]-([AT]*2/2),0))";
    private static final String FORMULA_13 =
        "IF([AZ]-[BA]+(SUM([U]:[AO]))>[AT]*4/2,[AT]*4/2-(SUM([U]:[AO])),[AZ]-[BA])";
    private static final String FORMULA_14 =
        "IF([BB]<=2,0,ROUNDDOWN([BB],0))";

    // ------------------------------------------------------------------
    // Case A: Normal, AT<=5 tier
    // ------------------------------------------------------------------
    @Test
    @DisplayName("Case A: Normal (AT<=5 tier) -> calc4=5")
    void caseA_normal() {
        assertCalcs(/*ar*/2, /*at*/5, emptyPo(), emptyArr(),
            8, 3, 5, 5,
            7, 3, 4, 4);
    }

    // ------------------------------------------------------------------
    // Case B: Stock不足 (low stock relative to demand), AT>10 tier
    // ------------------------------------------------------------------
    @Test
    @DisplayName("Case B: Stock不足 (AT>10 tier) -> calc4=20")
    void caseB_stockShortage() {
        assertCalcs(/*ar*/0, /*at*/20, emptyPo(), emptyArr(),
            60, 40, 20, 20,
            56, 40, 16, 16);
    }

    // ------------------------------------------------------------------
    // Case C: Stock余剰 (AR far exceeds AT) -> calc4 clamps to 0
    // ------------------------------------------------------------------
    @Test
    @DisplayName("Case C: Stock余剰 (AR >> AT) -> calc4=0 (clamped)")
    void caseC_stockSurplus() {
        assertCalcs(/*ar*/50, /*at*/10, emptyPo(), emptyArr(),
            -25, 0, -25, 0,
            -27, 0, -27, 0);
    }

    // ------------------------------------------------------------------
    // Case E: 0値 (AT=0, AR=0)
    // ------------------------------------------------------------------
    @Test
    @DisplayName("Case E: 0値 -> all zero")
    void caseE_zeroValues() {
        assertCalcs(/*ar*/0, /*at*/0, emptyPo(), emptyArr(),
            0, 0, 0, 0,
            0, 0, 0, 0);
    }

    // ------------------------------------------------------------------
    // Case F: Boundary AT=10 (still "<=10" tier)
    // ------------------------------------------------------------------
    @Test
    @DisplayName("Case F: Boundary AT=10 (<=10 tier)")
    void caseF_boundaryAt10() {
        assertCalcs(/*ar*/5, /*at*/10, emptyPo(), emptyArr(),
            20, 10, 10, 10,
            18, 10, 8, 8);
    }

    // ------------------------------------------------------------------
    // Case F2: Boundary AT=11 (crosses into ">10" tier)
    // ------------------------------------------------------------------
    @Test
    @DisplayName("Case F2: Boundary AT=11 (>10 tier)")
    void caseF2_boundaryAt11() {
        assertCalcs(/*ar*/5, /*at*/11, emptyPo(), emptyArr(),
            28, 17, 11, 11,
            26, 17, 9, 9);
    }

    // ------------------------------------------------------------------
    // Case G: Open PO/Arrival quantity large enough to trigger the
    // calc3 "exceeds threshold" cap branch (Open Quantity business rule)
    // ------------------------------------------------------------------
    @Test
    @DisplayName("Case G: Open PO/Arrival caps calc3/calc4")
    void caseG_openQuantityCap() {
        assertCalcs(/*ar*/0, /*at*/20, Arrays.asList(15, 10), Collections.singletonList(0),
            60, 40, 15, 15,
            56, 40, 7, 7);
    }

    // ------------------------------------------------------------------
    // Case D: Formula未設定 -> Default fallback via the public
    // StockCalculationHelper.calculateAllCalcs() entry point
    // (formula11..14 = null triggers the Default-4EU substitution,
    //  same inputs as Case A -> same expected results as Case A).
    // ------------------------------------------------------------------
    @Test
    @DisplayName("Case D: Formula未設定 falls back to Default 4EU formula (via calculateAllCalcs)")
    void caseD_defaultFormulaFallback() {
        JSONObject json = new JSONObject();
        StockCalculationHelper.calculateAllCalcs(
            json,
            /*logicalQty*/ 2,
            /*stkStandard*/ 5,
            /*leadTime*/ null,
            /*stkRate*/ null,
            emptyPo(),
            emptyArr(),
            /*formula11*/ null,
            /*formula12*/ null,
            /*formula13*/ null,
            /*formula14*/ null
        );

        assertEquals(8, json.get("calc1"));
        assertEquals(3, json.get("calc2"));
        assertEquals(5, json.get("calc3"));
        assertEquals(5, json.get("calc4"));
        assertEquals(7, json.get("calc1Alt"));
        assertEquals(3, json.get("calc2Alt"));
        assertEquals(4, json.get("calc3Alt"));
        assertEquals(4, json.get("calc4Alt"));
    }

    @Test
    @DisplayName("calc1 returns null when FORMULA_11 cannot be parsed (missing formula, no fallback applied at this level)")
    void calc1_nullWhenFormulaUnparseable() {
        assertNull(OrderQuantityCalculator.calc1(2, 5, null));
        assertNull(OrderQuantityCalculator.calc1(2, 5, ""));
        assertNull(OrderQuantityCalculator.calc1(2, 5, "not a formula"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void assertCalcs(int ar, int at, List<Integer> poQty, List<Integer> arrQty,
                              int expCalc1, int expCalc2, int expCalc3, int expCalc4,
                              int expCalc1Alt, int expCalc2Alt, int expCalc3Alt, int expCalc4Alt) {
        Integer calc1 = OrderQuantityCalculator.calc1(ar, at, FORMULA_11);
        Integer calc2 = OrderQuantityCalculator.calc2(calc1, at, FORMULA_12);
        Integer calc3 = OrderQuantityCalculator.calc3(calc1, calc2, at, poQty, arrQty, FORMULA_13);
        Integer calc4 = OrderQuantityCalculator.calc4(calc3, FORMULA_14);

        Integer calc1Alt = OrderQuantityCalculator.calc1Alt(ar, at, FORMULA_11);
        Integer calc2Alt = OrderQuantityCalculator.calc2Alt(calc1Alt, at);
        Integer calc3Alt = OrderQuantityCalculator.calc3Alt(calc1Alt, calc2Alt, at, poQty, arrQty);
        Integer calc4Alt = OrderQuantityCalculator.calc4Alt(calc3Alt);

        assertEquals(expCalc1, calc1, "calc1");
        assertEquals(expCalc2, calc2, "calc2");
        assertEquals(expCalc3, calc3, "calc3");
        assertEquals(expCalc4, calc4, "calc4");
        assertEquals(expCalc1Alt, calc1Alt, "calc1Alt");
        assertEquals(expCalc2Alt, calc2Alt, "calc2Alt");
        assertEquals(expCalc3Alt, calc3Alt, "calc3Alt");
        assertEquals(expCalc4Alt, calc4Alt, "calc4Alt");

        // Also verify the orchestrator entry point produces the same values end-to-end.
        JSONObject json = new JSONObject();
        StockCalculationHelper.calculateAllCalcs(json, ar, at, null, null, poQty, arrQty,
            FORMULA_11, FORMULA_12, FORMULA_13, FORMULA_14);
        assertEquals(expCalc4, json.get("calc4"), "calc4 via calculateAllCalcs");
        assertEquals(expCalc4Alt, json.get("calc4Alt"), "calc4Alt via calculateAllCalcs");
    }

    private static List<Integer> emptyPo() {
        return Collections.emptyList();
    }

    private static List<Integer> emptyArr() {
        return Collections.emptyList();
    }
}
