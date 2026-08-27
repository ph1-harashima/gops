// PORTED VERBATIM FROM LEGACY G-SYS.
// Source: phasep-gulliver/gulliver/src/main/java/jp/ne/glv/utilities/FormulaParser.java
// Only the package declaration below was changed (jp.ne.glv.utilities -> com.glv.gsysportal.legacy.calc)
// to relocate the file into the new project. NO other line was modified.
// DO NOT change any parsing/calculation logic in this file. See docs/G-SYS_Online-Ordering_Prototype_Technical_Design.md 4.3.
package com.glv.gsysportal.legacy.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for MS_FORMULA strings (FORMULA_11 through FORMULA_14).
 * Extracts multipliers and thresholds from Excel-style formulas.
 *
 * This class handles parsing of custom formulas stored in the MS_FORMULA table
 * and converts them into numeric parameters for order quantity calculations.
 *
 * @author Phase1 Philippines Inc.
 */
public class FormulaParser {

    private static final Logger logger = LoggerFactory.getLogger(FormulaParser.class);

    // Regex patterns
    private static final String PATTERN_AT_MULTIPLIER = "\\[AT\\]\\*(\\d+)/2";
    private static final String PATTERN_AT_MULTIPLIER_DECIMAL = "\\[AT\\]\\*(\\d+(?:\\.\\d+)?)/2";
    private static final String PATTERN_BB_THRESHOLD = "\\[BB\\]<=(\\d+)";
    private static final String PATTERN_BC_THRESHOLD = "\\[BC\\]<=(\\d+)";

    // Default formula for calc1_alt when no formula exists (BE: uses AT*0.8 base, same multipliers as AZ)
    private static final String DEFAULT_CALC1_ALT_FORMULA =
    "IF([AT]<=5,(ROUNDUP([AT]*0.8-[AR]+[AT]*2/2,0)),IF([AT]<=10,(ROUNDUP([AT]*0.8-[AR]+[AT]*3/2,0)),ROUNDUP([AT]*0.8-[AR]+[AT]*4/2,0)))";

    // Private constructor - utility class pattern
    private FormulaParser() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========================================
    // Public Parsing Methods
    // ========================================

    /**
     * Parse FORMULA_11 string and extract multipliers for calc1.
     *
     * <p>Supports both 2-condition and 3-condition formulas:</p>
     * <ul>
     *   <li>2-condition: IF([AT]<=5,...,else) → [m1, m2, m2]</li>
     *   <li>3-condition: IF([AT]<=5,...,IF([AT]<=10,...,else)) → [m1, m2, m3]</li>
     * </ul>
     *
     * @param formula11 FORMULA_11 string from MS_FORMULA table
     * @return Array of 3 doubles [≤5, ≤10, >10], or null if parsing fails
     */
    public static double[] parseFormula11Multipliers(String formula11) {
        if (isNullOrEmpty(formula11)) {
            logger.debug("FORMULA_11 is null or empty");
            return null;
        }

        try {
            Pattern pattern = Pattern.compile(PATTERN_AT_MULTIPLIER);
            Matcher matcher = pattern.matcher(formula11);

            List<Double> multipliers = new ArrayList<>();
            while (matcher.find()) {
                int numerator = Integer.parseInt(matcher.group(1));
                multipliers.add(numerator / 2.0);
            }

            // Convert to 3-element array based on conditions found
            if (multipliers.size() == 2) {
                // 2-condition: duplicate last value
                return new double[]{multipliers.get(0), multipliers.get(1), multipliers.get(1)};
            } else if (multipliers.size() == 3) {
                // 3-condition: all distinct
                return new double[]{multipliers.get(0), multipliers.get(1), multipliers.get(2)};
            } else {
                logger.error("Expected 2-3 multipliers in FORMULA_11, found {}: {}", multipliers.size(), formula11);
                return null;
            }

        } catch (Exception e) {
            logger.error("Error parsing FORMULA_11: {}", formula11, e);
            return null;
        }
    }

    /**
     * Parse FORMULA_12 string and extract multiplier for calc2.
     *
     * <p>Supports two formats:</p>
     * <ul>
     *   <li>Simple numeric: "1.0" or "2"</li>
     *   <li>Complex formula: "IF(ROUNDDOWN([AZ]-([AT]*2/2),0)<0,0,...)"</li>
     * </ul>
     *
     * @param formula12 FORMULA_12 string from MS_FORMULA table
     * @return Multiplier as Double, or null if parsing fails
     */
    public static Double parseFormula12Multiplier(String formula12) {
        if (isNullOrEmpty(formula12)) {
            logger.debug("FORMULA_12 is null or empty");
            return null;
        }

        try {
            // Try simple numeric format first
            return Double.parseDouble(formula12.trim());
        } catch (NumberFormatException e) {
            // Fall back to pattern extraction
            logger.debug("FORMULA_12 is not a simple number, extracting from formula");
            return extractMultiplier(formula12, "FORMULA_12");
        }
    }

    /**
     * Parse FORMULA_13 string and extract threshold multipliers for calc3.
     *
     * <p>Supports two formats:</p>
     * <ul>
     *   <li>Simple CSV: "2.0,2.0"</li>
     *   <li>Complex formula: "IF([BA]+[BE]>[AT]*4/2,[AT]*4/2-[BE],[BA])"</li>
     * </ul>
     *
     * @param formula13 FORMULA_13 string from MS_FORMULA table
     * @return Array of 2 doubles [param1, param2], or null if parsing fails
     */
    public static double[] parseFormula13Multipliers(String formula13) {
        if (isNullOrEmpty(formula13)) {
            logger.debug("FORMULA_13 is null or empty");
            return null;
        }

        try {
            // Try CSV format first
            if (formula13.contains(",") && !formula13.contains("IF")) {
                String[] parts = formula13.split(",");
                if (parts.length == 2) {
                    double param1 = Double.parseDouble(parts[0].trim());
                    double param2 = Double.parseDouble(parts[1].trim());
                    logger.debug("Parsed FORMULA_13 CSV: [{}, {}]", param1, param2);
                    return new double[]{param1, param2};
                }
            }

            // Extract from complex formula
            Pattern pattern = Pattern.compile(PATTERN_AT_MULTIPLIER_DECIMAL);
            Matcher matcher = pattern.matcher(formula13);

            List<Double> params = new ArrayList<>();
            while (matcher.find()) {
                double numerator = Double.parseDouble(matcher.group(1));
                params.add(numerator / 2.0);
            }

            if (params.size() >= 2) {
                return new double[]{params.get(0), params.get(1)};
            } else if (params.size() == 1) {
                // Use same value for both
                return new double[]{params.get(0), params.get(0)};
            }

            logger.warn("Could not extract parameters from FORMULA_13: {}", formula13);
            return null;

        } catch (Exception e) {
            logger.error("Error parsing FORMULA_13: {}", formula13, e);
            return null;
        }
    }

    /**
     * Parse FORMULA_14 string and extract threshold for calc4.
     *
     * <p>Supports multiple formats:</p>
     * <ul>
     *   <li>Simple numeric: "2"</li>
     *   <li>Complex with [BB]: "IF([BB]<=2,0,[BB])"</li>
     *   <li>Complex with [BC]: "IF([BC]<=2,0,ROUNDDOWN([BB],0))"</li>
     * </ul>
     *
     * @param formula14 FORMULA_14 string from MS_FORMULA table
     * @return Threshold as Integer, or null if parsing fails
     */
    public static Integer parseFormula14Threshold(String formula14) {
        if (isNullOrEmpty(formula14)) {
            logger.debug("FORMULA_14 is null or empty");
            return null;
        }

        try {
            // Try simple numeric format first
            return Integer.parseInt(formula14.trim());
        } catch (NumberFormatException e) {
            // Try [BB] pattern
            Pattern patternBB = Pattern.compile(PATTERN_BB_THRESHOLD);
            Matcher matcherBB = patternBB.matcher(formula14);
            if (matcherBB.find()) {
                return Integer.parseInt(matcherBB.group(1));
            }

            // Try [BC] pattern
            Pattern patternBC = Pattern.compile(PATTERN_BC_THRESHOLD);
            Matcher matcherBC = patternBC.matcher(formula14);
            if (matcherBC.find()) {
                return Integer.parseInt(matcherBC.group(1));
            }

            logger.warn("Could not extract threshold from FORMULA_14: {}", formula14);
            return null;
        }
    }

    /**
     * Transform FORMULA_11 to calc1_alt by changing base multiplier from 1 to 0.8.
     * Keeps the condition-based multipliers (AT*2/2, AT*3/2, etc.) unchanged.
     *
     * <p>Example: "[AT]*1-[AR]+[AT]*2/2" → "[AT]*0.8-[AR]+[AT]*2/2"</p>
     *
     * @param formula11 Original calc1 formula from MS_FORMULA table
     * @return calc1_alt formula with AT*0.8 base, or default if input is invalid
     */
    public static String generateCalc1AltFormula(String formula11) {
        if (isNullOrEmpty(formula11)) {
            logger.debug("FORMULA_11 is null or empty, using default calc1_alt formula");
            return DEFAULT_CALC1_ALT_FORMULA;
        }

        try {
            // Replace base multiplier [AT]*1- with [AT]*0.8- (change from 1 to 0.8)
            String result = formula11.replaceAll("\\[AT\\]\\*1-", "[AT]*0.8-");
            return result;

        } catch (Exception e) {
            logger.error("Error generating calc1_alt from FORMULA_11: {}", formula11, e);
            return DEFAULT_CALC1_ALT_FORMULA;
        }
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Extract multiplier from complex formula using decimal pattern.
     */
    private static Double extractMultiplier(String formula, String formulaName) {
        try {
            Pattern pattern = Pattern.compile(PATTERN_AT_MULTIPLIER_DECIMAL);
            Matcher matcher = pattern.matcher(formula);

            if (matcher.find()) {
                double numerator = Double.parseDouble(matcher.group(1));
                return numerator / 2.0;
            }

            logger.warn("Could not extract multiplier from {}: {}", formulaName, formula);
            return null;

        } catch (Exception e) {
            logger.error("Error parsing {}: {}", formulaName, formula, e);
            return null;
        }
    }

    /**
     * Check if string is null or empty.
     */
    private static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
