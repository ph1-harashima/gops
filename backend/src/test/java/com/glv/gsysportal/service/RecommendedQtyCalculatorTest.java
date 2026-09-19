package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierRegionClassification;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BR-09 (docs/gulliver-20260917-confirmed-business-rules.md): Recommended
 * Qty Strategy dispatch - pure Mockito unit test (mocks
 * {@link SupplierRegionClassificationResolutionService} directly, mirroring
 * {@code OfficialPoNumberGeneratorTest}'s own idiom) so it never needs a
 * real Spring context/DB just to prove the dispatch rule itself.
 */
class RecommendedQtyCalculatorTest {

    private static LegacyStockRow row(String supplierCd, String brandCd) {
        return new LegacyStockRow(
                "SKU-1", "Item One", brandCd, "Brand One", "30", "NEW", false,
                10, 5, 20, 0, 0, 0,
                null, null, null, null,
                supplierCd, "Supplier One", BigDecimal.TEN, "JPY", LocalDateTime.now());
    }

    @Test
    void unclassifiedSupplierResolvesToOverseasStrategy_neverAGuessedDomesticNumber() {
        SupplierRegionClassificationResolutionService regionResolutionService =
                mock(SupplierRegionClassificationResolutionService.class);
        when(regionResolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR")).thenReturn(null);

        RecommendedQtyCalculator calculator = new RecommendedQtyCalculator(regionResolutionService);
        LegacyStockRow row = row("SUP_ALPHA", "BR_OUTDOOR");

        // Same value the Overseas Strategy (Legacy calc1-4, unconditional
        // pre-Strategy behavior) would produce directly - proves an
        // unclassified Supplier's Recommended Qty is byte-for-byte
        // unchanged from before this Strategy split existed.
        assertEquals(new OverseasRecommendedQtyStrategy().calculate(row), calculator.calc4(row));
    }

    @Test
    void overseasClassifiedSupplierUsesLegacyFormula() {
        SupplierRegionClassificationResolutionService regionResolutionService =
                mock(SupplierRegionClassificationResolutionService.class);
        when(regionResolutionService.resolve("SUP_BETA", "BR_HOME")).thenReturn(SupplierRegionClassification.OVERSEAS);

        RecommendedQtyCalculator calculator = new RecommendedQtyCalculator(regionResolutionService);
        LegacyStockRow row = row("SUP_BETA", "BR_HOME");

        assertEquals(new OverseasRecommendedQtyStrategy().calculate(row), calculator.calc4(row));
    }

    @Test
    void domesticClassifiedSupplierReturnsNull_neverAppliesTheOverseasFormula() {
        SupplierRegionClassificationResolutionService regionResolutionService =
                mock(SupplierRegionClassificationResolutionService.class);
        when(regionResolutionService.resolve("SUP_GAMMA", "BR_KITCHEN")).thenReturn(SupplierRegionClassification.DOMESTIC);

        RecommendedQtyCalculator calculator = new RecommendedQtyCalculator(regionResolutionService);
        LegacyStockRow row = row("SUP_GAMMA", "BR_KITCHEN");

        assertNull(calculator.calc4(row), "BR-09: Domestic's calculation Rule is unconfirmed - must never silently borrow the Overseas formula");
    }
}
