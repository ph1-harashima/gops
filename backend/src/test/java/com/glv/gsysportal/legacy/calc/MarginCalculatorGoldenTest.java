package com.glv.gsysportal.legacy.calc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Golden Test for the verbatim-ported Legacy margin logic
 * ({@link MarginCalculator}, ported from {@code Formula.PRC_SELL()}/
 * {@code Formula.PROFIT_RATE_SELL(...)}).
 *
 * Same honest disclosure as {@code OrderQuantityCalculatorGoldenTest}: Legacy's
 * own {@code jp.ne.glv.utilities.FormulaTest.java} is entirely commented out
 * AND (re-confirmed this Phase, re-reading the file directly) contains no
 * {@code assertEquals}/{@code assertThat} calls at all even inside the
 * comment - it is a manual {@code System.out.println}/{@code System.err.println}
 * "print and eyeball" harness, not an automated Golden Test. There is
 * therefore no pre-existing expected-value baseline to reuse. The expected
 * values below were derived by hand, directly from the ported formula body
 * and BigDecimal's documented HALF_UP/DOWN rounding semantics, and
 * cross-checked with a second independent by-hand long-division derivation
 * for every non-trivial case.
 */
class MarginCalculatorGoldenTest {

    // ------------------------------------------------------------------
    // prcSell(): tax-included -> tax-excluded, divide by 1.10, HALF_UP, scale 0
    // ------------------------------------------------------------------

    @Test
    @DisplayName("prcSell: exact division (1100 / 1.10 = 1000.0) -> 1000")
    void prcSell_exact() {
        assertEquals(0, new BigDecimal("1000").compareTo(MarginCalculator.prcSell(new BigDecimal("1100.00"))));
    }

    @Test
    @DisplayName("prcSell: fractional remainder below .5 truncates down (1000/1.10 = 909.0909...) -> 909")
    void prcSell_roundsDown() {
        assertEquals(0, new BigDecimal("909").compareTo(MarginCalculator.prcSell(new BigDecimal("1000.00"))));
    }

    @Test
    @DisplayName("prcSell: fractional remainder >= .5 rounds up (1930/1.10 = 1754.5454...) -> 1755")
    void prcSell_roundsUp() {
        assertEquals(0, new BigDecimal("1755").compareTo(MarginCalculator.prcSell(new BigDecimal("1930.00"))));
    }

    @Test
    @DisplayName("prcSell: null input -> null")
    void prcSell_null() {
        assertNull(MarginCalculator.prcSell(null));
    }

    // ------------------------------------------------------------------
    // profitRateSell(): non-free-ship path (result = Var2 = (PRC_SELL - COST) / PRC_SELL)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("profitRateSell: no free ship, positive margin (3236 - 2096) / 3236 = 0.3522 (truncated)")
    void profitRateSell_normalPositiveMargin() {
        BigDecimal prcSell = MarginCalculator.prcSell(new BigDecimal("3560.00")); // -> 3236
        assertEquals(0, new BigDecimal("3236").compareTo(prcSell));
        BigDecimal rate = MarginCalculator.profitRateSell(
                prcSell, new BigDecimal("2096.00"), null, null, new BigDecimal("3560.00"));
        assertEquals(0, new BigDecimal("0.3522").compareTo(rate));
    }

    @Test
    @DisplayName("profitRateSell: negative margin (selling below cost) - no exception, no threshold, just a negative rate")
    void profitRateSell_negativeMargin() {
        BigDecimal prcSell = MarginCalculator.prcSell(new BigDecimal("3500.00")); // 3500/1.10=3181.818... -> 3182
        assertEquals(0, new BigDecimal("3182").compareTo(prcSell));
        BigDecimal rate = MarginCalculator.profitRateSell(
                prcSell, new BigDecimal("3603.00"), null, null, new BigDecimal("3500.00"));
        // (3182 - 3603) / 3182 = -421/3182 = -0.132306... truncated toward zero (DOWN) -> -0.1323
        assertEquals(0, new BigDecimal("-0.1323").compareTo(rate));
    }

    // ------------------------------------------------------------------
    // profitRateSell(): free-ship path, PRC_SELL_W_TAX <= 9999 -> same as Var2 (ship fee NOT subtracted)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("profitRateSell: free ship, price <= 9999 -> ship fee is NOT subtracted (still Var2)")
    void profitRateSell_freeShipLowPrice() {
        BigDecimal prcSell = MarginCalculator.prcSell(new BigDecimal("1930.00")); // -> 1755
        BigDecimal rate = MarginCalculator.profitRateSell(
                prcSell, new BigDecimal("1137.00"), true, new BigDecimal("300.00"), new BigDecimal("1930.00"));
        // (1755 - 1137) / 1755 = 618/1755 = 0.352136... truncated -> 0.3521
        assertEquals(0, new BigDecimal("0.3521").compareTo(rate));
    }

    // ------------------------------------------------------------------
    // profitRateSell(): free-ship path, PRC_SELL_W_TAX > 9999 -> ship fee IS subtracted
    // ------------------------------------------------------------------

    @Test
    @DisplayName("profitRateSell: free ship, price > 9999 -> ship fee IS subtracted from the margin numerator")
    void profitRateSell_freeShipHighPriceSubtractsShipFee() {
        BigDecimal prcSell = MarginCalculator.prcSell(new BigDecimal("12000.00")); // 12000/1.10=10909.0909...->10909
        assertEquals(0, new BigDecimal("10909").compareTo(prcSell));
        BigDecimal rate = MarginCalculator.profitRateSell(
                prcSell, new BigDecimal("7000.00"), true, new BigDecimal("500.00"), new BigDecimal("12000.00"));
        // (10909 - 7000 - 500) / 10909 = 3409/10909 = 0.312494... truncated -> 0.3124
        assertEquals(0, new BigDecimal("0.3124").compareTo(rate));
    }

    // ------------------------------------------------------------------
    // profitRateSell(): null-safety (division-by-zero avoidance, not a Business Rule)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("profitRateSell: null cost -> null (RE 6章: division-by-zero guard only, no warning semantics)")
    void profitRateSell_nullCost() {
        BigDecimal prcSell = MarginCalculator.prcSell(new BigDecimal("1930.00"));
        assertNull(MarginCalculator.profitRateSell(prcSell, null, null, null, new BigDecimal("1930.00")));
    }

    @Test
    @DisplayName("profitRateSell: zero cost -> null (matches Legacy's intValue()==0 check)")
    void profitRateSell_zeroCost() {
        BigDecimal prcSell = MarginCalculator.prcSell(new BigDecimal("1930.00"));
        assertNull(MarginCalculator.profitRateSell(prcSell, BigDecimal.ZERO, null, null, new BigDecimal("1930.00")));
    }

    @Test
    @DisplayName("profitRateSell: null prcSell (e.g. null prcSellWTax upstream) -> null")
    void profitRateSell_nullPrcSell() {
        assertNull(MarginCalculator.profitRateSell(null, new BigDecimal("2096.00"), null, null, null));
    }

    // ------------------------------------------------------------------
    // compute(): the Create/Edit screen's combined breakdown
    // ------------------------------------------------------------------

    @Test
    @DisplayName("compute: full breakdown for a normal SKU (matches the 2 tests above combined)")
    void compute_normalSku() {
        MarginCalculator.MarginBreakdown breakdown = MarginCalculator.compute(
                new BigDecimal("3560.00"), new BigDecimal("2096.00"), null, null);
        assertEquals(0, new BigDecimal("3560.00").compareTo(breakdown.sellingPriceWTax()));
        assertEquals(0, new BigDecimal("2096.00").compareTo(breakdown.costWTax()));
        assertEquals(0, new BigDecimal("1464.00").compareTo(breakdown.marginAmount())); // 3560.00 - 2096.00
        assertEquals(0, new BigDecimal("0.3522").compareTo(breakdown.marginRate()));
    }

    @Test
    @DisplayName("compute: missing cost -> marginAmount and marginRate both null, sellingPrice still populated")
    void compute_missingCost() {
        MarginCalculator.MarginBreakdown breakdown = MarginCalculator.compute(
                new BigDecimal("3560.00"), null, null, null);
        assertEquals(0, new BigDecimal("3560.00").compareTo(breakdown.sellingPriceWTax()));
        assertNull(breakdown.costWTax());
        assertNull(breakdown.marginAmount());
        assertNull(breakdown.marginRate());
    }

    @Test
    @DisplayName("compute: negative margin does not throw and is not clamped/blocked (Phase 8-B forbids Threshold Rules)")
    void compute_negativeMarginIsJustDisplayed() {
        MarginCalculator.MarginBreakdown breakdown = MarginCalculator.compute(
                new BigDecimal("3500.00"), new BigDecimal("3603.00"), null, null);
        assertEquals(0, new BigDecimal("-103.00").compareTo(breakdown.marginAmount()));
        assertEquals(0, new BigDecimal("-0.1323").compareTo(breakdown.marginRate()));
    }
}
