package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.MailTemplate;
import com.glv.gsysportal.repository.prototype.MailTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 7-C3 7章: pure unit test (Mockito-stubbed Repository, no Spring
 * context/DB) for the AMBIGUOUS branch specifically. The real
 * mail_template table's partial UNIQUE index (V10 migration) prevents this
 * state from ever being created through the Service in practice (see
 * MailTemplateServiceIntegrationTest.dbUniqueIndexRejectsASecondActiveTemplateAtTheSamePriorityTier) -
 * this test proves the Resolution algorithm itself still handles it
 * correctly as defense-in-depth (e.g. against future data imported around
 * the constraint), isolated from that DB-level guarantee.
 */
class MailTemplateResolutionServiceTest {

    private static MailTemplate template(long id) {
        MailTemplate t = new MailTemplate();
        t.setId(id);
        return t;
    }

    @Test
    void twoActiveTemplatesAtTheSamePriorityTierIsAmbiguous() {
        MailTemplateRepository repo = mock(MailTemplateRepository.class);
        when(repo.findAllByTemplateTypeAndSupplierCodeIsNullAndBrandCodeIsNullAndLanguageAndActiveTrue(
                eq(MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER), eq("ja")))
                .thenReturn(List.of(template(1L), template(2L)));

        MailTemplateResolutionService service = new MailTemplateResolutionService(repo);
        MailTemplateResolution resolution = service.resolve(MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER, null, null, "ja");

        assertEquals(MailTemplateResolution.Kind.AMBIGUOUS, resolution.kind());
    }

    @Test
    void resolvesTheSingleMatchAtTheDefaultTierWhenNoMoreSpecificTierMatches() {
        MailTemplateRepository repo = mock(MailTemplateRepository.class);
        MailTemplate expected = template(5L);
        when(repo.findAllByTemplateTypeAndSupplierCodeAndBrandCodeAndLanguageAndActiveTrue(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(repo.findAllByTemplateTypeAndSupplierCodeAndBrandCodeIsNullAndLanguageAndActiveTrue(any(), any(), any()))
                .thenReturn(List.of());
        when(repo.findAllByTemplateTypeAndSupplierCodeIsNullAndBrandCodeIsNullAndLanguageAndActiveTrue(any(), any()))
                .thenReturn(List.of(expected));

        MailTemplateResolutionService service = new MailTemplateResolutionService(repo);
        MailTemplateResolution resolution = service.resolve(MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER, "SUP_ALPHA", "BR_OUTDOOR", "ja");

        assertEquals(MailTemplateResolution.Kind.RESOLVED, resolution.kind());
        assertSame(expected, resolution.template());
    }

    @Test
    void brandSpecificTierShortCircuitsBeforeCheckingLessSpecificTiers() {
        MailTemplateRepository repo = mock(MailTemplateRepository.class);
        MailTemplate expected = template(9L);
        when(repo.findAllByTemplateTypeAndSupplierCodeAndBrandCodeAndLanguageAndActiveTrue(any(), any(), any(), any()))
                .thenReturn(List.of(expected));

        MailTemplateResolutionService service = new MailTemplateResolutionService(repo);
        MailTemplateResolution resolution = service.resolve(MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER, "SUP_ALPHA", "BR_OUTDOOR", "ja");

        assertEquals(MailTemplateResolution.Kind.RESOLVED, resolution.kind());
        assertSame(expected, resolution.template());
    }

    @Test
    void notFoundWhenEveryTierIsEmpty() {
        MailTemplateRepository repo = mock(MailTemplateRepository.class);
        when(repo.findAllByTemplateTypeAndSupplierCodeAndBrandCodeAndLanguageAndActiveTrue(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(repo.findAllByTemplateTypeAndSupplierCodeAndBrandCodeIsNullAndLanguageAndActiveTrue(any(), any(), any()))
                .thenReturn(List.of());
        when(repo.findAllByTemplateTypeAndSupplierCodeIsNullAndBrandCodeIsNullAndLanguageAndActiveTrue(any(), any()))
                .thenReturn(List.of());

        MailTemplateResolutionService service = new MailTemplateResolutionService(repo);
        MailTemplateResolution resolution = service.resolve(MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER, "SUP_ALPHA", "BR_OUTDOOR", "ja");

        assertEquals(MailTemplateResolution.Kind.NOT_FOUND, resolution.kind());
        assertTrue(resolution.template() == null);
    }
}
