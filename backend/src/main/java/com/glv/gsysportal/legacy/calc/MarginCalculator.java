package com.glv.gsysportal.legacy.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Margin / Margin Rate calculation, ported from Legacy G-SYS.
 *
 * Source: phasep-gulliver/gulliver/src/main/java/jp/ne/glv/utilities/Formula.java
 * ({@code PRC_SELL()} and {@code PROFIT_RATE_SELL(BigDecimal, BigDecimal,
 * Boolean, BigDecimal, BigDecimal)}, confirmed in
 * docs/legacy-price-change-reverse-engineering.md 6章). Unlike
 * {@link StockCalculationHelper}/{@link OrderQuantityCalculator} (verbatim
 * ports of an ENTIRE Legacy class), only these two methods are ported here -
 * {@code Formula.java} as a whole is a 15-field stateful builder covering
 * customs/tariff calculation ({@code CUST_AMT_JPY}/{@code CUST_AMT_TRIFF_TAX}),
 * order-qty calc1-4 (already independently ported as
 * {@link OrderQuantityCalculator}), and EC-mall-specific price formulas
 * ({@code PRC_SELL_P15_W_TAX}/{@code PRC_SELL_AMZ_W_TAX}/etc., depend on
 * {@code MsStk} which does not exist in this codebase) that Phase 8-B's
 * Target Design (docs/target-price-change-workflow.md 10章) explicitly scopes
 * OUT of Price Change Foundation ("Cost/Selling Price/Margin/Margin Rateの分離"
 * - only Margin/Margin Rate on the Sell price are in scope). Porting the whole
 * class would not compile (missing {@code MsStk}) and would duplicate
 * {@link OrderQuantityCalculator}; porting only the 2 needed methods keeps
 * "ロジックを勝手に改変せず" (do not alter the calculation) while staying
 * buildable.
 *
 * <p><b>The method bodies below are character-for-character identical to
 * Legacy</b> (variable names {@code Var1}/{@code Var2} kept exactly as-is,
 * including Legacy's own {@code new Boolean(false)}/{@code new BigDecimal(0)}
 * autoboxing style) except: (1) converted from instance methods reading
 * {@code this.*} fields to static methods taking explicit parameters (this
 * class has no stateful Formula object, matching how
 * {@code LegacyPriceReadRepository} already supplies these values as
 * individual row columns rather than a rebuilt Legacy entity graph), and
 * (2) {@code BigDecimal.ROUND_DOWN} (deprecated int constant) replaced with
 * the equivalent {@code RoundingMode.DOWN} where Legacy itself already used
 * the enum overload one line prior, to avoid one avoidable deprecation
 * warning in new code - the ROUNDING BEHAVIOR is identical either way.
 *
 * <p>Confirmed Source fact (RE 6章): if {@code costThisMonthAvg} is null or
 * zero, this returns {@code null} (division-by-zero avoidance only) - there
 * is NO threshold/warning/blocking logic in Legacy for a negative result
 * (i.e. selling below cost), and Phase 8-B does not add one (forbidden by
 * Phase 8-B Section 5 - "赤字警告Threshold...は一切追加しない").
 */
public final class MarginCalculator {

    private MarginCalculator() {
    }

    /** Formula.java's {@code PRC_LIST()}/{@code PRC_SELL()}/{@code PRC_SALE()}
     * all divide by the same hardcoded 1.10 (10% consumption tax, Legacy's own
     * former 8% branch is commented out in Source) - only the Sell variant is
     * ported since Sale/List price are out of Foundation scope (class Javadoc). */
    public static BigDecimal prcSell(BigDecimal prcSellWTax) {
        if (prcSellWTax == null) {
            return null;
        }
        return prcSellWTax.divide(BigDecimal.valueOf(1.10), 0, RoundingMode.HALF_UP);
    }

    /**
     * Verbatim port of {@code Formula.PROFIT_RATE_SELL(BigDecimal PRC_SELL,
     * BigDecimal COST_THIS_MONTH_AVG, Boolean FREE_SHIP_FLG, BigDecimal
     * SHIP_FEE, BigDecimal PRC_SELL_W_TAX)}.
     */
    public static BigDecimal profitRateSell(BigDecimal prcSell, BigDecimal costThisMonthAvg,
                                             Boolean freeShipFlg, BigDecimal shipFee, BigDecimal prcSellWTax) {
        if (prcSell == null || prcSell.intValue() == 0) {
            return null;
        }
        if (costThisMonthAvg == null || costThisMonthAvg.intValue() == 0) {
            return null;
        }

        if (freeShipFlg == null) {
            freeShipFlg = Boolean.FALSE;
        }

        if (shipFee == null) {
            shipFee = BigDecimal.ZERO;
        }

        BigDecimal Var1 = prcSell.subtract(costThisMonthAvg);
        BigDecimal Var2 = Var1.divide(prcSell, 4, RoundingMode.DOWN);
        BigDecimal result;
        if (freeShipFlg) {
            if (prcSellWTax.compareTo(BigDecimal.valueOf(9999)) <= 0) {
                result = Var2;
            } else {
                result = (Var1.subtract(shipFee)).divide(prcSell, 4, RoundingMode.DOWN);
            }
        } else {
            result = Var2;
        }
        return result;
    }

    /**
     * Convenience wrapper combining {@link #prcSell} + {@link #profitRateSell}
     * plus the tax-INCLUDED margin amount (Sell price minus Cost, both
     * tax-included - Legacy's own {@code costThisMonthAvg} is a tax-included
     * landed-cost average, RE 5章) into a single result for the Price Change
     * Create/Edit screen's "Current/Proposed Margin Preview" (Target Design
     * 10章's display table: 旧価格|新価格|差額|変更率|原価|利益|利益率).
     * Pure display data - no threshold/warning classification is attached.
     */
    public static MarginBreakdown compute(BigDecimal prcSellWTax, BigDecimal costThisMonthAvg,
                                           Boolean freeShipFlg, BigDecimal shipFee) {
        BigDecimal prcSell = prcSell(prcSellWTax);
        BigDecimal marginAmount = (prcSellWTax == null || costThisMonthAvg == null)
                ? null
                : prcSellWTax.subtract(costThisMonthAvg);
        BigDecimal profitRateSell = profitRateSell(prcSell, costThisMonthAvg, freeShipFlg, shipFee, prcSellWTax);
        return new MarginBreakdown(prcSellWTax, costThisMonthAvg, marginAmount, profitRateSell);
    }

    /** @param sellingPriceWTax the tax-included selling price this breakdown was computed for
     * @param costWTax the tax-included cost (Legacy {@code COST_THIS_MONTH_AVG}) used
     * @param marginAmount {@code sellingPriceWTax - costWTax} (null if either input is null)
     * @param marginRate Legacy's {@code PROFIT_RATE_SELL} result (null if cost is null/zero) */
    public record MarginBreakdown(BigDecimal sellingPriceWTax, BigDecimal costWTax,
                                   BigDecimal marginAmount, BigDecimal marginRate) {
    }
}
