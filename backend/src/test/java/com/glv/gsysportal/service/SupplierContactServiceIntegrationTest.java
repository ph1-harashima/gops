package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.dto.request.SupplierContactRequest;
import com.glv.gsysportal.dto.response.SupplierContactResponse;
import com.glv.gsysportal.exception.BrandCodeNotFoundException;
import com.glv.gsysportal.exception.DuplicateSupplierContactException;
import com.glv.gsysportal.exception.InvalidContactTypeException;
import com.glv.gsysportal.exception.InvalidEmailFormatException;
import com.glv.gsysportal.exception.InvalidLanguageException;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import com.glv.gsysportal.exception.SupplierContactNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 7-C3 2章/4章/14章: Contact CRUD, Legacy Code Validation. Whole-test-
 * method Prototype transaction, always rolled back. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class SupplierContactServiceIntegrationTest {

    private static final String ADMIN = "admin-tester";

    @Autowired
    private SupplierContactService service;

    private static SupplierContactRequest request(String supplierCode, String brandCode, String email) {
        return new SupplierContactRequest(supplierCode, brandCode, "Contact Name", email,
                SupplierContact.CONTACT_TYPE_TO, SupplierContact.LANGUAGE_JA, null, null, true, true);
    }

    @Test
    void createsAndListsAContact() {
        SupplierContactResponse created = service.create(request("SUP_ALPHA", "BR_OUTDOOR", "contact@example.com"), ADMIN);

        assertEquals("SUP_ALPHA", created.supplierCode());
        assertEquals("BR_OUTDOOR", created.brandCode());
        assertTrue(created.active());
        assertEquals(ADMIN, created.createdBy());
        assertTrue(service.list().stream().anyMatch(c -> c.id().equals(created.id())));
    }

    @Test
    void rejectsUnknownSupplierCode() {
        assertThrows(SupplierCodeNotFoundException.class,
                () -> service.create(request("SUP_DOES_NOT_EXIST", null, "x@example.com"), ADMIN));
    }

    @Test
    void rejectsUnknownBrandCode() {
        assertThrows(BrandCodeNotFoundException.class,
                () -> service.create(request("SUP_ALPHA", "BR_DOES_NOT_EXIST", "x@example.com"), ADMIN));
    }

    @Test
    void rejectsInvalidEmailFormat() {
        assertThrows(InvalidEmailFormatException.class,
                () -> service.create(request("SUP_ALPHA", null, "not-an-email"), ADMIN));
    }

    @Test
    void rejectsInvalidContactType() {
        SupplierContactRequest bad = new SupplierContactRequest("SUP_ALPHA", null, "N", "x@example.com",
                "BCC", SupplierContact.LANGUAGE_JA, null, null, false, true);
        assertThrows(InvalidContactTypeException.class, () -> service.create(bad, ADMIN));
    }

    @Test
    void rejectsInvalidLanguage() {
        SupplierContactRequest bad = new SupplierContactRequest("SUP_ALPHA", null, "N", "x@example.com",
                SupplierContact.CONTACT_TYPE_TO, "fr", null, null, false, true);
        assertThrows(InvalidLanguageException.class, () -> service.create(bad, ADMIN));
    }

    @Test
    void rejectsDuplicateActiveContactSameSupplierBrandEmail() {
        service.create(request("SUP_ALPHA", "BR_OUTDOOR", "dup@example.com"), ADMIN);

        assertThrows(DuplicateSupplierContactException.class,
                () -> service.create(request("SUP_ALPHA", "BR_OUTDOOR", "DUP@example.com"), ADMIN),
                "email comparison must be case-insensitive");
    }

    @Test
    void allowsReAddingAPreviouslyDeactivatedContact() {
        SupplierContactResponse created = service.create(request("SUP_ALPHA", "BR_OUTDOOR", "reuse@example.com"), ADMIN);
        SupplierContactRequest deactivate = new SupplierContactRequest("SUP_ALPHA", "BR_OUTDOOR", "Contact Name",
                "reuse@example.com", SupplierContact.CONTACT_TYPE_TO, SupplierContact.LANGUAGE_JA, null, null, true, false);
        service.update(created.id(), deactivate, ADMIN);

        // Now inactive - re-adding the same identity must succeed.
        SupplierContactResponse recreated = service.create(request("SUP_ALPHA", "BR_OUTDOOR", "reuse@example.com"), ADMIN);
        assertTrue(recreated.active());
    }

    @Test
    void updateChangesFieldsAndPreservesCreatedBy() {
        SupplierContactResponse created = service.create(request("SUP_ALPHA", "BR_OUTDOOR", "edit@example.com"), ADMIN);

        SupplierContactRequest edited = new SupplierContactRequest("SUP_ALPHA", "BR_OUTDOOR", "Edited Name",
                "edit@example.com", SupplierContact.CONTACT_TYPE_CC, SupplierContact.LANGUAGE_EN, "APAC", "IMPORT", false, true);
        SupplierContactResponse updated = service.update(created.id(), edited, "editor-tester");

        assertEquals("Edited Name", updated.contactName());
        assertEquals(SupplierContact.CONTACT_TYPE_CC, updated.contactType());
        assertEquals(SupplierContact.LANGUAGE_EN, updated.language());
        assertEquals(ADMIN, updated.createdBy());
        assertEquals("editor-tester", updated.updatedBy());
    }

    @Test
    void updateOnUnknownIdThrowsNotFound() {
        assertThrows(SupplierContactNotFoundException.class,
                () -> service.update(999_999L, request("SUP_ALPHA", null, "x@example.com"), ADMIN));
    }

    @Test
    void supplierOnlyContactAllowsNullBrandCode() {
        SupplierContactResponse created = service.create(request("SUP_ALPHA", null, "supplierwide@example.com"), ADMIN);
        assertFalse(created.brandCode() != null);
    }
}
