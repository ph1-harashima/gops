package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierRegionClassification;
import com.glv.gsysportal.dto.request.SupplierRegionClassificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章):
 * Resolution priority - mirrors ManufacturerChannelResolutionServiceIntegrationTest's
 * own precedence tests exactly. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class SupplierRegionClassificationResolutionServiceIntegrationTest {

    private static final String ADMIN = "admin-tester";

    @Autowired
    private SupplierRegionClassificationService classificationService;
    @Autowired
    private SupplierRegionClassificationResolutionService resolutionService;

    @Test
    void noMasterRowResolvesNull_neverGuessed() {
        assertNull(resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void brandSpecificTierWinsOverSupplierOnlyTier() {
        classificationService.create(new SupplierRegionClassificationRequest("SUP_ALPHA", null, SupplierRegionClassification.DOMESTIC, true), ADMIN);
        classificationService.create(new SupplierRegionClassificationRequest("SUP_ALPHA", "BR_OUTDOOR", SupplierRegionClassification.OVERSEAS, true), ADMIN);

        assertEquals(SupplierRegionClassification.OVERSEAS, resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void fallsBackToSupplierOnlyTierWhenNoBrandSpecificMatch() {
        classificationService.create(new SupplierRegionClassificationRequest("SUP_ALPHA", null, SupplierRegionClassification.DOMESTIC, true), ADMIN);

        assertEquals(SupplierRegionClassification.DOMESTIC, resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void inactiveRowsAreNeverResolved() {
        var created = classificationService.create(
                new SupplierRegionClassificationRequest("SUP_ALPHA", "BR_OUTDOOR", SupplierRegionClassification.OVERSEAS, true), ADMIN);
        classificationService.update(created.id(),
                new SupplierRegionClassificationRequest("SUP_ALPHA", "BR_OUTDOOR", SupplierRegionClassification.OVERSEAS, false), ADMIN);

        assertNull(resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR"));
    }
}
