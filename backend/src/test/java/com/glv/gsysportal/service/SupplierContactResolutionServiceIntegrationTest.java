package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.dto.request.SupplierContactRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 7-C3 5章: Resolution priority (brand-specific tier wins over
 * supplier-only tier; multiple active rows in the winning tier are all
 * returned, not treated as ambiguous). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class SupplierContactResolutionServiceIntegrationTest {

    private static final String ADMIN = "admin-tester";

    @Autowired
    private SupplierContactService contactService;
    @Autowired
    private SupplierContactResolutionService resolutionService;

    private static SupplierContactRequest request(String supplierCode, String brandCode, String email, String type) {
        return new SupplierContactRequest(supplierCode, brandCode, "Name " + email, email, type,
                SupplierContact.LANGUAGE_JA, null, null, false, true);
    }

    @Test
    void noContactsResolvesEmpty() {
        List<SupplierContact> resolved = resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR", SupplierContact.CONTACT_TYPE_TO);
        assertTrue(resolved.isEmpty());
    }

    @Test
    void brandSpecificTierWinsOverSupplierOnlyTier() {
        contactService.create(request("SUP_ALPHA", null, "supplier-wide@example.com", SupplierContact.CONTACT_TYPE_TO), ADMIN);
        contactService.create(request("SUP_ALPHA", "BR_OUTDOOR", "brand-specific@example.com", SupplierContact.CONTACT_TYPE_TO), ADMIN);

        List<SupplierContact> resolved = resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR", SupplierContact.CONTACT_TYPE_TO);

        assertEquals(1, resolved.size());
        assertEquals("brand-specific@example.com", resolved.get(0).getEmail());
    }

    @Test
    void fallsBackToSupplierOnlyTierWhenNoBrandSpecificMatch() {
        contactService.create(request("SUP_ALPHA", null, "supplier-wide@example.com", SupplierContact.CONTACT_TYPE_TO), ADMIN);

        List<SupplierContact> resolved = resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR", SupplierContact.CONTACT_TYPE_TO);

        assertEquals(1, resolved.size());
        assertEquals("supplier-wide@example.com", resolved.get(0).getEmail());
    }

    @Test
    void multipleActiveContactsInTheWinningTierAreAllReturned_notAmbiguous() {
        contactService.create(request("SUP_ALPHA", "BR_OUTDOOR", "one@example.com", SupplierContact.CONTACT_TYPE_TO), ADMIN);
        contactService.create(request("SUP_ALPHA", "BR_OUTDOOR", "two@example.com", SupplierContact.CONTACT_TYPE_TO), ADMIN);

        List<SupplierContact> resolved = resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR", SupplierContact.CONTACT_TYPE_TO);

        assertEquals(2, resolved.size());
    }

    @Test
    void inactiveContactsAreNeverResolved() {
        var created = contactService.create(request("SUP_ALPHA", "BR_OUTDOOR", "inactive@example.com", SupplierContact.CONTACT_TYPE_TO), ADMIN);
        contactService.update(created.id(), new SupplierContactRequest("SUP_ALPHA", "BR_OUTDOOR", "Name",
                "inactive@example.com", SupplierContact.CONTACT_TYPE_TO, SupplierContact.LANGUAGE_JA, null, null, false, false), ADMIN);

        assertTrue(resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR", SupplierContact.CONTACT_TYPE_TO).isEmpty());
    }

    @Test
    void toAndCcAreResolvedIndependently() {
        contactService.create(request("SUP_ALPHA", "BR_OUTDOOR", "to@example.com", SupplierContact.CONTACT_TYPE_TO), ADMIN);
        contactService.create(request("SUP_ALPHA", "BR_OUTDOOR", "cc@example.com", SupplierContact.CONTACT_TYPE_CC), ADMIN);

        List<SupplierContact> to = resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR", SupplierContact.CONTACT_TYPE_TO);
        List<SupplierContact> cc = resolutionService.resolve("SUP_ALPHA", "BR_OUTDOOR", SupplierContact.CONTACT_TYPE_CC);

        assertEquals("to@example.com", to.get(0).getEmail());
        assertEquals("cc@example.com", cc.get(0).getEmail());
    }
}
