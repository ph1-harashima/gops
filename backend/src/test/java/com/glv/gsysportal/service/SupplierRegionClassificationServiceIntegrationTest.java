package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierRegionClassification;
import com.glv.gsysportal.dto.request.SupplierRegionClassificationRequest;
import com.glv.gsysportal.dto.response.SupplierRegionClassificationResponse;
import com.glv.gsysportal.exception.BrandCodeNotFoundException;
import com.glv.gsysportal.exception.DuplicateSupplierRegionClassificationException;
import com.glv.gsysportal.exception.InvalidSupplierRegionClassificationException;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import com.glv.gsysportal.exception.SupplierRegionClassificationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章):
 * Region Classification Master CRUD - mirrors
 * ManufacturerChannelServiceIntegrationTest's own coverage shape exactly. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class SupplierRegionClassificationServiceIntegrationTest {

    private static final String ADMIN = "admin-tester";

    @Autowired
    private SupplierRegionClassificationService service;

    @Test
    void createRejectsInvalidValue() {
        assertThrows(InvalidSupplierRegionClassificationException.class, () -> service.create(
                new SupplierRegionClassificationRequest("SUP_ALPHA", null, "MARS", true), ADMIN));
    }

    @Test
    void createRejectsUnknownSupplierCode() {
        assertThrows(SupplierCodeNotFoundException.class, () -> service.create(
                new SupplierRegionClassificationRequest("NO_SUCH_SUPPLIER", null, SupplierRegionClassification.DOMESTIC, true), ADMIN));
    }

    @Test
    void createRejectsUnknownBrandCode() {
        assertThrows(BrandCodeNotFoundException.class, () -> service.create(
                new SupplierRegionClassificationRequest("SUP_ALPHA", "NO_SUCH_BRAND", SupplierRegionClassification.DOMESTIC, true), ADMIN));
    }

    @Test
    void createAndListRoundTrips() {
        SupplierRegionClassificationResponse created = service.create(
                new SupplierRegionClassificationRequest("SUP_ALPHA", "BR_OUTDOOR", SupplierRegionClassification.OVERSEAS, true), ADMIN);

        assertEquals(SupplierRegionClassification.OVERSEAS, created.regionClassification());
        assertTrue(service.list().stream().anyMatch(c -> c.id().equals(created.id())));
    }

    @Test
    void createRejectsDuplicateActiveIdentity() {
        service.create(new SupplierRegionClassificationRequest("SUP_ALPHA", "BR_OUTDOOR", SupplierRegionClassification.DOMESTIC, true), ADMIN);

        assertThrows(DuplicateSupplierRegionClassificationException.class, () -> service.create(
                new SupplierRegionClassificationRequest("SUP_ALPHA", "BR_OUTDOOR", SupplierRegionClassification.OVERSEAS, true), ADMIN));
    }

    @Test
    void reAddingAfterDeactivatingIsAllowed() {
        SupplierRegionClassificationResponse created = service.create(
                new SupplierRegionClassificationRequest("SUP_ALPHA", "BR_OUTDOOR", SupplierRegionClassification.DOMESTIC, true), ADMIN);
        service.update(created.id(), new SupplierRegionClassificationRequest("SUP_ALPHA", "BR_OUTDOOR", SupplierRegionClassification.DOMESTIC, false), ADMIN);

        SupplierRegionClassificationResponse recreated = service.create(
                new SupplierRegionClassificationRequest("SUP_ALPHA", "BR_OUTDOOR", SupplierRegionClassification.OVERSEAS, true), ADMIN);
        assertEquals(SupplierRegionClassification.OVERSEAS, recreated.regionClassification());
    }

    @Test
    void updateOnUnknownIdThrowsNotFound() {
        assertThrows(SupplierRegionClassificationNotFoundException.class, () -> service.update(999_999L,
                new SupplierRegionClassificationRequest("SUP_ALPHA", null, SupplierRegionClassification.DOMESTIC, true), ADMIN));
    }
}
