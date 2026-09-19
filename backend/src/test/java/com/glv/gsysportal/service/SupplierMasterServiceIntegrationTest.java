package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.request.ManufacturerChannelRequest;
import com.glv.gsysportal.dto.request.SupplierContactRequest;
import com.glv.gsysportal.dto.request.SupplierRegionClassificationRequest;
import com.glv.gsysportal.dto.response.SupplierMasterDetailResponse;
import com.glv.gsysportal.dto.response.SupplierMasterSummaryResponse;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
 * pure READ aggregation over Legacy Supplier Master + existing Contact/
 * Channel/Region/PO Short Code services - no new write path, so this test
 * only exercises {@code listSuppliers}/{@code getSupplier} plus the existing
 * services' own {@code create} to set up fixtures (their own CRUD/validation
 * is already covered by their own dedicated test classes).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class SupplierMasterServiceIntegrationTest {

    private static final String ADMIN = "admin-tester";

    @Autowired
    private SupplierMasterService service;
    @Autowired
    private SupplierContactService supplierContactService;
    @Autowired
    private ManufacturerChannelService manufacturerChannelService;
    @Autowired
    private SupplierRegionClassificationService supplierRegionClassificationService;

    @Test
    void listSuppliersReturnsOnlyLegacyRegisteredSuppliers_neverFabricated() {
        List<SupplierMasterSummaryResponse> suppliers = service.listSuppliers();

        // The Legacy Demo Master is exactly SUP_ALPHA/SUP_BETA/SUP_GAMMA
        // (docs/gops-information-architecture-cross-screen-audit.md 4.3章
        // confirmed this directly against ms_comm CATE_ID='MS_SUPPL') -
        // asserting the exact set (not just "contains") proves nothing is
        // guessed/invented beyond what Legacy actually registers.
        assertEquals(
                List.of("SUP_ALPHA", "SUP_BETA", "SUP_GAMMA"),
                suppliers.stream().map(SupplierMasterSummaryResponse::supplierCode).sorted().toList());
        assertTrue(suppliers.stream().allMatch(s -> s.supplierName() != null && !s.supplierName().isBlank()));
    }

    @Test
    void listSuppliersReflectsConfiguredContactChannelRegionPoCode() {
        supplierContactService.create(
                new SupplierContactRequest("SUP_ALPHA", "BR_OUTDOOR", "Hub Test Contact",
                        "hub-test@example.com", "TO", "ja", null, null, false, true),
                ADMIN);
        manufacturerChannelService.create(
                new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", "EMAIL", true), ADMIN);
        supplierRegionClassificationService.create(
                new SupplierRegionClassificationRequest("SUP_ALPHA", "BR_OUTDOOR", "OVERSEAS", true), ADMIN);

        SupplierMasterSummaryResponse alpha = service.listSuppliers().stream()
                .filter(s -> "SUP_ALPHA".equals(s.supplierCode()))
                .findFirst().orElseThrow();

        assertTrue(alpha.contactConfigured());
        assertEquals("EMAIL", alpha.channel());
        assertEquals("OVERSEAS", alpha.regionClassification());
    }

    @Test
    void listSuppliersShowsMixedWhenBrandsUnderTheSameSupplierDisagree() {
        manufacturerChannelService.create(
                new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", "EMAIL", true), ADMIN);
        manufacturerChannelService.create(
                new ManufacturerChannelRequest("SUP_ALPHA", "BR_HOME", "EDI", true), ADMIN);

        SupplierMasterSummaryResponse alpha = service.listSuppliers().stream()
                .filter(s -> "SUP_ALPHA".equals(s.supplierCode()))
                .findFirst().orElseThrow();

        assertEquals("MIXED", alpha.channel());
    }

    @Test
    void listSuppliersShowsMissingWhenNothingConfigured() {
        SupplierMasterSummaryResponse gamma = service.listSuppliers().stream()
                .filter(s -> "SUP_GAMMA".equals(s.supplierCode()))
                .findFirst().orElseThrow();

        // Contact/Channel/Region genuinely have no active row for SUP_GAMMA
        // in the Demo fixture. officialPoShortCode is the one exception -
        // V30's demo seed pre-registers a Short Code for every Legacy
        // Supplier/Brand (BR-08 auto-numbering needs one to function at
        // all), so SUP_GAMMA's is already "GAM", not Missing.
        assertFalse(gamma.contactConfigured());
        assertEquals(null, gamma.channel());
        assertEquals(null, gamma.regionClassification());
        assertEquals("GAM", gamma.officialPoShortCode());
    }

    @Test
    void getSupplierRejectsUnknownSupplierCode() {
        assertThrows(SupplierCodeNotFoundException.class, () -> service.getSupplier("NO_SUCH_SUPPLIER"));
    }

    @Test
    void getSupplierReturnsBrandListAndActiveContactCount() {
        // Ground truth (docs/gops-information-architecture-cross-screen-audit.md
        // 4.3章's Legacy query, re-verified directly against tr_po/tr_po_dtl):
        // SUP_ALPHA supplies BR_KITCHEN and BR_OUTDOOR, never BR_HOME.
        supplierContactService.create(
                new SupplierContactRequest("SUP_ALPHA", "BR_KITCHEN", "Hub Test Contact 1",
                        "hub-test-1@example.com", "TO", "ja", null, null, false, true),
                ADMIN);
        supplierContactService.create(
                new SupplierContactRequest("SUP_ALPHA", "BR_KITCHEN", "Hub Test Contact 2",
                        "hub-test-2@example.com", "CC", "ja", null, null, false, true),
                ADMIN);

        SupplierMasterDetailResponse alpha = service.getSupplier("SUP_ALPHA");

        assertEquals("SUP_ALPHA", alpha.supplierCode());
        assertTrue(alpha.contactConfigured());
        assertEquals(2, alpha.activeContactCount());
        assertTrue(alpha.brands().stream().anyMatch(b -> "BR_KITCHEN".equals(b.brandCode())),
                "SUP_ALPHA/BR_KITCHEN Order Candidates already exist in the Demo fixture, so the Brand must be visible here");
        assertTrue(alpha.brands().stream().anyMatch(b -> "BR_OUTDOOR".equals(b.brandCode())),
                "SUP_ALPHA/BR_OUTDOOR Order Candidates already exist in the Demo fixture, so the Brand must be visible here");
        assertFalse(alpha.brands().stream().anyMatch(b -> "BR_HOME".equals(b.brandCode())),
                "SUP_ALPHA never supplies BR_HOME in the Demo fixture - must not be fabricated");
    }

    @Test
    void inactiveRowsNeverCountAsConfigured() {
        var contact = supplierContactService.create(
                new SupplierContactRequest("SUP_GAMMA", "BR_OUTDOOR", "Soon Inactive",
                        "soon-inactive@example.com", "TO", "ja", null, null, false, true),
                ADMIN);
        supplierContactService.update(contact.id(),
                new SupplierContactRequest("SUP_GAMMA", "BR_OUTDOOR", "Soon Inactive",
                        "soon-inactive@example.com", "TO", "ja", null, null, false, false),
                ADMIN);

        Optional<SupplierMasterSummaryResponse> gamma = service.listSuppliers().stream()
                .filter(s -> "SUP_GAMMA".equals(s.supplierCode()))
                .findFirst();

        assertTrue(gamma.isPresent());
        assertFalse(gamma.get().contactConfigured(), "a deactivated Contact must not count as Configured");
    }
}
