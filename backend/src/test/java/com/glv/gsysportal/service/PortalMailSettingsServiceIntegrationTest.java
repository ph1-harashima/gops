package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.PortalMailSettingsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gap Analysis §11 (docs/gulliver-20260917-phase1-gap-analysis.md 11章):
 * Default CC Foundation - a prefill-only setting, never an enforced
 * "always CC" Business Rule (this Service has no caller from
 * EmailSendService - verified structurally by EmailSendService's own
 * constructor not depending on this Service at all).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class PortalMailSettingsServiceIntegrationTest {

    private static final String ADMIN = "admin-tester";

    @Autowired
    private PortalMailSettingsService service;

    @Test
    void defaultsToEmptyBeforeAnyUpdate() {
        PortalMailSettingsResponse settings = service.get();

        assertEquals(List.of(), settings.defaultCc());
    }

    @Test
    void updateSetsAndPersistsDefaultCc() {
        service.update(List.of("cc1@example.com", "cc2@example.com"), ADMIN);

        PortalMailSettingsResponse settings = service.get();
        assertEquals(List.of("cc1@example.com", "cc2@example.com"), settings.defaultCc());
        assertEquals(ADMIN, settings.updatedBy());
    }

    @Test
    void updateWithEmptyListClearsDefaultCc() {
        service.update(List.of("cc1@example.com"), ADMIN);
        service.update(List.of(), ADMIN);

        assertEquals(List.of(), service.get().defaultCc());
    }

    @Test
    void blankEntriesAreFilteredOut() {
        service.update(List.of("cc1@example.com", "  ", ""), ADMIN);

        assertEquals(List.of("cc1@example.com"), service.get().defaultCc());
    }

    @Test
    void repeatedUpdatesOverwriteTheSameSingletonRow() {
        service.update(List.of("first@example.com"), ADMIN);
        service.update(List.of("second@example.com"), ADMIN);

        assertEquals(List.of("second@example.com"), service.get().defaultCc());
    }
}
