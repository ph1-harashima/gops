package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.MailTemplate;
import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.dto.request.MailTemplateRequest;
import com.glv.gsysportal.dto.response.MailTemplateResponse;
import com.glv.gsysportal.exception.BrandCodeNotFoundException;
import com.glv.gsysportal.exception.InvalidLanguageException;
import com.glv.gsysportal.exception.InvalidTemplateTypeException;
import com.glv.gsysportal.exception.MailTemplateNotFoundException;
import com.glv.gsysportal.exception.SupplierCodeNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 7-C3 6章/14章: Template CRUD, Legacy Code Validation. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class MailTemplateServiceIntegrationTest {

    private static final String ADMIN = "admin-tester";

    @Autowired
    private MailTemplateService service;

    private static MailTemplateRequest request(String supplierCode, String brandCode, String language) {
        return new MailTemplateRequest("Default PO Template", MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER,
                supplierCode, brandCode, language, "PO {{poNo}}", "Dear {{contactName}}, ...",
                "OFFICIAL_PO_EXCEL", true);
    }

    @Test
    void createsAndListsATemplate() {
        MailTemplateResponse created = service.create(request(null, null, SupplierContact.LANGUAGE_JA), ADMIN);

        assertEquals(MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER, created.templateType());
        assertTrue(service.list().stream().anyMatch(t -> t.id().equals(created.id())));
    }

    @Test
    void rejectsInvalidTemplateType() {
        MailTemplateRequest bad = new MailTemplateRequest("N", "NOT_A_TYPE", null, null,
                SupplierContact.LANGUAGE_JA, "S", "B", null, true);
        assertThrows(InvalidTemplateTypeException.class, () -> service.create(bad, ADMIN));
    }

    @Test
    void rejectsInvalidLanguage() {
        MailTemplateRequest bad = new MailTemplateRequest("N", MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER, null, null,
                "fr", "S", "B", null, true);
        assertThrows(InvalidLanguageException.class, () -> service.create(bad, ADMIN));
    }

    @Test
    void rejectsUnknownSupplierCode() {
        assertThrows(SupplierCodeNotFoundException.class,
                () -> service.create(request("SUP_DOES_NOT_EXIST", null, SupplierContact.LANGUAGE_JA), ADMIN));
    }

    @Test
    void rejectsUnknownBrandCode() {
        assertThrows(BrandCodeNotFoundException.class,
                () -> service.create(request("SUP_ALPHA", "BR_DOES_NOT_EXIST", SupplierContact.LANGUAGE_JA), ADMIN));
    }

    @Test
    void languageDefaultTemplateAllowsNullSupplierAndBrand() {
        MailTemplateResponse created = service.create(request(null, null, SupplierContact.LANGUAGE_JA), ADMIN);
        assertEquals(null, created.supplierCode());
    }

    @Test
    void updateOnUnknownIdThrowsNotFound() {
        assertThrows(MailTemplateNotFoundException.class,
                () -> service.update(999_999L, request(null, null, SupplierContact.LANGUAGE_JA), ADMIN));
    }

    @Test
    void dbUniqueIndexRejectsASecondActiveTemplateAtTheSamePriorityTier() {
        // 7-C3 7章: Resolution must never be ambiguous by construction going
        // forward - the migration's partial unique index is the real Backend
        // enforcement here (MailTemplateResolutionService's AMBIGUOUS branch
        // is defense-in-depth for any pre-existing/edge-case data).
        service.create(request(null, null, SupplierContact.LANGUAGE_JA), ADMIN);

        assertThrows(DataIntegrityViolationException.class,
                () -> service.create(request(null, null, SupplierContact.LANGUAGE_JA), ADMIN));
    }
}
