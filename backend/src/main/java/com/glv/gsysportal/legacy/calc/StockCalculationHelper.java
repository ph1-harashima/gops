// PORTED VERBATIM FROM LEGACY G-SYS.
// Source: phasep-gulliver/gulliver/src/main/java/jp/ne/glv/utilities/StockCalculationHelper.java
// Only the package declaration below was changed (jp.ne.glv.utilities -> com.glv.gsysportal.legacy.calc)
// to relocate the file into the new project. NO other line was modified.
// Depends on org.json.simple.JSONObject (json-simple), added as a Maven dependency for this exact reason.
// DO NOT change any calculation logic in this file. See docs/G-SYS_Online-Ordering_Prototype_Technical_Design.md 4.3.
package com.glv.gsysportal.legacy.calc;

import java.math.BigDecimal;
import java.util.List;
import org.json.simple.JSONObject;

/**
 * Helper class for orchestrating stock-related calculations.
 *
 * @author Phase1 Philippines Inc.
 */
public class StockCalculationHelper {

    private static final String DEFAULT_FORMULA_11_4EU = "IF([AT]<=5,(ROUNDUP([AT]*1-[AR]+[AT]*2/2,0)),IF([AT]<=10,(ROUNDUP([AT]*1-[AR]+[AT]*3/2,0)),ROUNDUP([AT]*1-[AR]+[AT]*4/2,0)))";
    private static final String DEFAULT_FORMULA_12_4EU = "IF(ROUNDDOWN([AZ]-([AT]*2/2),0)<0,0,ROUNDDOWN([AZ]-([AT]*2/2),0))";
    private static final String DEFAULT_FORMULA_13_4EU = "IF([AZ]-[BA]+(SUM([U]:[AO]))>[AT]*4/2,[AT]*4/2-(SUM([U]:[AO])),[AZ]-[BA])";
    private static final String DEFAULT_FORMULA_14_4EU = "IF([BB]<=2,0,ROUNDDOWN([BB],0))";

    /**
     * Calculate all order quantity formulas (calc1-4 and calc1Alt-4Alt).
     *
     * @param json Target JSON object to store results
     * @param logicalQty Logical quantity (AR column)
     * @param stkStandard Stock standard (AT column)
     * @param leadTime Lead time code (not used in current implementation)
     * @param stkRate Stock rate (not used in current implementation)
     * @param poQtyList List of poQty1-20 values
     * @param arrQtyList List of arrQty1-10 values
     * @param formula11 FORMULA_11 from MS_FORMULA (calc1)
     * @param formula12 FORMULA_12 from MS_FORMULA (calc2)
     * @param formula13 FORMULA_13 from MS_FORMULA (calc3)
     * @param formula14 FORMULA_14 from MS_FORMULA (calc4)
     */
    @SuppressWarnings("unchecked")
    public static void calculateAllCalcs(JSONObject json,
                                         Integer logicalQty,
                                         Integer stkStandard,
                                         String leadTime,
                                         BigDecimal stkRate,
                                         List<Integer> poQtyList,
                                         List<Integer> arrQtyList,
                                         String formula11,
                                         String formula12,
                                         String formula13,
                                         String formula14) {

        if (isBlank(formula11) && isBlank(formula12) && isBlank(formula13) && isBlank(formula14)) {
            formula11 = DEFAULT_FORMULA_11_4EU;
            formula12 = DEFAULT_FORMULA_12_4EU;
            formula13 = DEFAULT_FORMULA_13_4EU;
            formula14 = DEFAULT_FORMULA_14_4EU;
        }

        // Standard calculations using MS_FORMULA
        Integer calc1 = OrderQuantityCalculator.calc1(logicalQty, stkStandard, formula11);
        Integer calc2 = OrderQuantityCalculator.calc2(calc1, stkStandard, formula12);
        Integer calc3 = OrderQuantityCalculator.calc3(calc1, calc2, stkStandard, poQtyList, arrQtyList, formula13);
        Integer calc4 = OrderQuantityCalculator.calc4(calc3, formula14);

        json.put("calc1", calc1);
        json.put("calc2", calc2);
        json.put("calc3", calc3);
        json.put("calc4", calc4);

        // Alternative calculations (hardcoded logic)
        Integer calc1Alt = OrderQuantityCalculator.calc1Alt(logicalQty, stkStandard, formula11);
        Integer calc2Alt = OrderQuantityCalculator.calc2Alt(calc1Alt, stkStandard);
        Integer calc3Alt = OrderQuantityCalculator.calc3Alt(calc1Alt, calc2Alt, stkStandard, poQtyList, arrQtyList);
        Integer calc4Alt = OrderQuantityCalculator.calc4Alt(calc3Alt);

        json.put("calc1Alt", calc1Alt);
        json.put("calc2Alt", calc2Alt);
        json.put("calc3Alt", calc3Alt);
        json.put("calc4Alt", calc4Alt);

    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
