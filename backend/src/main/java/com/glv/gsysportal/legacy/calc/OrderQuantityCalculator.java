// PORTED VERBATIM FROM LEGACY G-SYS.
// Source: phasep-gulliver/gulliver/src/main/java/jp/ne/glv/utilities/OrderQuantityCalculator.java
// Only the package declaration below was changed (jp.ne.glv.utilities -> com.glv.gsysportal.legacy.calc)
// to relocate the file into the new project. NO other line was modified.
// DO NOT change any calculation logic in this file. See docs/G-SYS_Online-Ordering_Prototype_Technical_Design.md 4.3.
package com.glv.gsysportal.legacy.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * * Calculator for order quantity formulas (calc1-4 and calc1Alt-4Alt).
 * Uses MS_FORMULA strings parsed by FormulaParser.
 *
 * <p>Business Rules:</p>
 * <ul>
 *   <li>Stock thresholds: ≤5 (low), ≤10 (medium), >10 (high)</li>
 *   <li>Alt calc1 uses a 0.8 base with the same threshold multipliers as calc1</li>
 * </ul>
 *
 * @author Phase1 Philippines Inc.
 */
public class OrderQuantityCalculator {

    private static final Logger logger = LoggerFactory.getLogger(OrderQuantityCalculator.class);

    // Private constructor - utility class pattern
    private OrderQuantityCalculator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========================================
    // Main Calculation Methods (calc1-4)
    // ========================================

    /**
     * Calculate calc1 using MS_FORMULA.
     * Formula: AT - AR + (AT × multiplier)
     *
     * @param logicalQty Logical quantity (AR column), null-safe
     * @param stkStandard Stock standard (AT column), null-safe
     * @param formula11 FORMULA_11 string from MS_FORMULA table
     * @return Calculated order quantity (rounded up), or null if formula is missing
     */
    public static Integer calc1(Integer logicalQty, Integer stkStandard, String formula11) {
        logicalQty = defaultIfNull(logicalQty);
        stkStandard = defaultIfNull(stkStandard);

        double[] multipliers = FormulaParser.parseFormula11Multipliers(formula11);
        if (multipliers == null) {
            logger.debug("calc1: No formula found");
            return null;
        }

        // Select multiplier based on stock standard (business rule: ≤5, ≤10, >10)
        double multiplier = (stkStandard <= 5) ? multipliers[0]
                          : (stkStandard <= 10) ? multipliers[1]
                          : multipliers[2];

        return new BigDecimal(stkStandard)
            .subtract(new BigDecimal(logicalQty))
            .add(new BigDecimal(stkStandard).multiply(new BigDecimal(multiplier)))
            .setScale(0, RoundingMode.UP)
            .intValue();
    }

    /**
     * Calculate calc2 using MS_FORMULA.
     * Formula: max(0, calc1 - AT × multiplier)
     *
     * @param calc1 Result from calc1
     * @param stkStandard Stock standard (AT column), null-safe
     * @param formula12 FORMULA_12 string from MS_FORMULA table
     * @return Calculated order quantity (rounded down), or null if calc1 or formula is missing
     */
    public static Integer calc2(Integer calc1, Integer stkStandard, String formula12) {
        if (calc1 == null) {
            logger.debug("calc2: calc1 is null");
            return null;
        }

        stkStandard = defaultIfNull(stkStandard);

        Double multiplier = FormulaParser.parseFormula12Multiplier(formula12);
        if (multiplier == null) {
            logger.debug("calc2: No formula found");
            return null;
        }

        double result = calc1 - (stkStandard * multiplier);
        return (result < 0) ? 0 : new BigDecimal(result).setScale(0, RoundingMode.DOWN).intValue();
    }

    /**
     * Calculate calc3 using MS_FORMULA.
     * Formula: IF((c1-c2)+poArr > AT×param1, AT×param2-poArr, c1-c2)
     *
     * @param calc1 Result from calc1
     * @param calc2 Result from calc2
     * @param stkStandard Stock standard (AT column), null-safe
     * @param poQtyList List of poQty1-20 values, null-safe
     * @param arrQtyList List of arrQty1-10 values, null-safe
     * @param formula13 FORMULA_13 string from MS_FORMULA table
     * @return Calculated order quantity, or null if required inputs are missing
     */
    public static Integer calc3(Integer calc1, Integer calc2, Integer stkStandard,
                                List<Integer> poQtyList, List<Integer> arrQtyList,
                                String formula13) {
        if (calc1 == null || calc2 == null) {
            logger.debug("calc3: calc1 or calc2 is null");
            return null;
        }

        stkStandard = defaultIfNull(stkStandard);
        int openQtyTotal = sumQuantities(poQtyList) + sumQuantities(arrQtyList);

        double[] params = FormulaParser.parseFormula13Multipliers(formula13);
        if (params == null) {
            logger.debug("calc3: No formula found");
            return null;
        }

        int c1MinusC2 = calc1 - calc2;

        // Business logic: check if order quantity exceeds threshold
        if (c1MinusC2 + openQtyTotal > stkStandard * params[0]) {
            return (int)(stkStandard * params[1]) - openQtyTotal;
        } else {
            return c1MinusC2;
        }
    }

    /**
     * Calculate calc4 using MS_FORMULA.
     * Formula: (calc3 <= threshold) ? 0 : calc3
     *
     * @param calc3 Result from calc3
     * @param formula14 FORMULA_14 string from MS_FORMULA table
     * @return Calculated order quantity, or null if calc3 or formula is missing
     */
    public static Integer calc4(Integer calc3, String formula14) {
        if (calc3 == null) {
            logger.debug("calc4: calc3 is null");
            return null;
        }

        Integer threshold = FormulaParser.parseFormula14Threshold(formula14);
        if (threshold == null) {
            logger.debug("calc4: No formula found");
            return null;
        }

        return (calc3 <= threshold) ? 0 : calc3;
    }

    // ========================================
    // Alternative Calculation Methods (calc1Alt-4Alt)
    // ========================================

    /**
     * Calculate calc1Alt using 0.8 as base and formula11 threshold multipliers.
     * Falls back to generated default-derived multipliers when formula11 is missing.
     *
     * @param logicalQty Logical quantity (AR column), null-safe
     * @param stkStandard Stock standard (AT column), null-safe
     * @param formula11 FORMULA_11 string from MS_FORMULA table
     * @return Calculated order quantity, or null if multipliers cannot be resolved
     */
    public static Integer calc1Alt(Integer logicalQty, Integer stkStandard, String formula11) {
        logicalQty = defaultIfNull(logicalQty);
        stkStandard = defaultIfNull(stkStandard);

        double[] multipliers = FormulaParser.parseFormula11Multipliers(formula11);
        if (multipliers == null) {
            logger.debug("calc1Alt: No formula found, using generated fallback formula");
            String formula11Alt = FormulaParser.generateCalc1AltFormula(formula11);
            multipliers = FormulaParser.parseFormula11Multipliers(formula11Alt);
            if (multipliers == null) {
                logger.debug("calc1Alt: Could not parse fallback formula");
                return null;
            }
        }

        // calc1Alt keeps the same threshold multipliers as calc1 but changes the base from 1.0 to 0.8.
        double multiplier = (stkStandard <= 5) ? multipliers[0]
                          : (stkStandard <= 10) ? multipliers[1]
                          : multipliers[2];

        return new BigDecimal(stkStandard)
            .multiply(BigDecimal.valueOf(0.8))
            .subtract(new BigDecimal(logicalQty))
            .add(new BigDecimal(stkStandard).multiply(BigDecimal.valueOf(multiplier)))
            .setScale(0, RoundingMode.UP)
            .intValue();
    }

    /**
     * Calculate calc2Alt using the adjusted multiplier (AT × 0.8).
     *
     * @param calc1Alt Result from calc1Alt
     * @param stkStandard Stock standard (AT column), null-safe
     * @return Calculated order quantity (rounded down), or null if calc1Alt is missing
     */
    public static Integer calc2Alt(Integer calc1Alt, Integer stkStandard) {
        if (calc1Alt == null) {
            logger.debug("calc2Alt: calc1Alt is null");
            return null;
        }

        stkStandard = defaultIfNull(stkStandard);
        // Apply AT * 0.8 as the base reduction to match calc1Alt changes
        double result = calc1Alt - (stkStandard * 0.8);
        return (result < 0) ? 0 : (int) Math.floor(result);
    }

    /**
     * Calculate calc3Alt using adjusted threshold (AT × 0.8 × 2).
     *
     * @param calc1Alt Result from calc1Alt
     * @param calc2Alt Result from calc2Alt
     * @param stkStandard Stock standard (AT column), null-safe
     * @param poQtyList List of poQty1-20 values, null-safe
     * @param arrQtyList List of arrQty1-10 values, null-safe
     * @return Calculated order quantity, or null if required inputs are missing
     */
    public static Integer calc3Alt(Integer calc1Alt, Integer calc2Alt, Integer stkStandard,
                                   List<Integer> poQtyList, List<Integer> arrQtyList) {
        if (calc1Alt == null || calc2Alt == null) {
            logger.debug("calc3Alt: calc1Alt or calc2Alt is null");
            return null;
        }

        stkStandard = defaultIfNull(stkStandard);
        int poArrSum = sumQuantities(poQtyList) + sumQuantities(arrQtyList);
        // Use AT * 0.8 * 2 to match updated calc1Alt base effect
        double threshold = stkStandard * 0.8 * 2.0;  // Adjusted threshold
        double condition = calc1Alt - calc2Alt + poArrSum;

        if (condition > threshold) {
            return (int) Math.floor(threshold - poArrSum);
        } else {
            return calc1Alt - calc2Alt;
        }
    }

    /**
     * Calculate calc4Alt using hardcoded threshold (≤2).
     *
     * @param calc3Alt Result from calc3Alt
     * @return Calculated order quantity, or null if calc3Alt is missing
     */
    public static Integer calc4Alt(Integer calc3Alt) {
        if (calc3Alt == null) {
            logger.debug("calc4Alt: calc3Alt is null");
            return null;
        }
        return (calc3Alt <= 2) ? 0 : (int) Math.floor(calc3Alt);  // Hardcoded threshold: 2
    }


    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Return 0 if quantity is null, otherwise return the quantity.
     */
    private static int defaultIfNull(Integer quantity) {
        return (quantity == null) ? 0 : quantity;
    }

    /**
     * Sum all quantities in a list, treating nulls as 0.
     */
    private static int sumQuantities(List<Integer> quantities) {
        if (quantities == null) {
            return 0;
        }
        int sum = 0;
        for (Integer qty : quantities) {
            sum += defaultIfNull(qty);
        }
        return sum;
    }
}
