package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.OfficialPoShortCode;
import com.glv.gsysportal.exception.OfficialPoShortCodeNotConfiguredException;
import com.glv.gsysportal.repository.prototype.OfficialPoShortCodeRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): pure unit
 * test for the composition rule itself
 * ({@code {SupplierShortCode}{BrandShortCode}{3-digit sequence}}) and the
 * "never invent a Short Code" guard - mocks both collaborators directly so
 * it never needs a real Spring context/DB, mirroring
 * {@code OfficialPoImportFolderPlacementRetryTest}'s own Mockito idiom. */
class OfficialPoNumberGeneratorTest {

    private static OfficialPoShortCode shortCode(String type, String businessCode, String value) {
        OfficialPoShortCode c = new OfficialPoShortCode();
        c.setCodeType(type);
        c.setBusinessCode(businessCode);
        c.setShortCode(value);
        c.setActive(true);
        return c;
    }

    @Test
    void composesSupplierShortCodePlusBrandShortCodePlusThreeDigitSequence() {
        OfficialPoShortCodeRepository shortCodeRepository = mock(OfficialPoShortCodeRepository.class);
        OfficialPoSequenceService sequenceService = mock(OfficialPoSequenceService.class);
        when(shortCodeRepository.findFirstByCodeTypeAndBusinessCodeAndActiveTrue(eq(OfficialPoShortCode.TYPE_SUPPLIER), eq("SUP_ALPHA")))
                .thenReturn(Optional.of(shortCode(OfficialPoShortCode.TYPE_SUPPLIER, "SUP_ALPHA", "ABC")));
        when(shortCodeRepository.findFirstByCodeTypeAndBusinessCodeAndActiveTrue(eq(OfficialPoShortCode.TYPE_BRAND), eq("BR_OUTDOOR")))
                .thenReturn(Optional.of(shortCode(OfficialPoShortCode.TYPE_BRAND, "BR_OUTDOOR", "XYZ")));
        when(sequenceService.nextSequence("SUP_ALPHA", "BR_OUTDOOR")).thenReturn(1);

        OfficialPoNumberGenerator generator = new OfficialPoNumberGenerator(shortCodeRepository, sequenceService);

        assertEquals("ABCXYZ001", generator.generate("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void padsTheSequenceToThreeDigits() {
        OfficialPoShortCodeRepository shortCodeRepository = mock(OfficialPoShortCodeRepository.class);
        OfficialPoSequenceService sequenceService = mock(OfficialPoSequenceService.class);
        when(shortCodeRepository.findFirstByCodeTypeAndBusinessCodeAndActiveTrue(eq(OfficialPoShortCode.TYPE_SUPPLIER), eq("SUP_ALPHA")))
                .thenReturn(Optional.of(shortCode(OfficialPoShortCode.TYPE_SUPPLIER, "SUP_ALPHA", "ABC")));
        when(shortCodeRepository.findFirstByCodeTypeAndBusinessCodeAndActiveTrue(eq(OfficialPoShortCode.TYPE_BRAND), eq("BR_OUTDOOR")))
                .thenReturn(Optional.of(shortCode(OfficialPoShortCode.TYPE_BRAND, "BR_OUTDOOR", "XYZ")));
        when(sequenceService.nextSequence("SUP_ALPHA", "BR_OUTDOOR")).thenReturn(7);

        OfficialPoNumberGenerator generator = new OfficialPoNumberGenerator(shortCodeRepository, sequenceService);

        assertEquals("ABCXYZ007", generator.generate("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void throwsWhenSupplierShortCodeNotConfigured_neverInventsOne() {
        OfficialPoShortCodeRepository shortCodeRepository = mock(OfficialPoShortCodeRepository.class);
        OfficialPoSequenceService sequenceService = mock(OfficialPoSequenceService.class);
        when(shortCodeRepository.findFirstByCodeTypeAndBusinessCodeAndActiveTrue(eq(OfficialPoShortCode.TYPE_SUPPLIER), eq("SUP_UNKNOWN")))
                .thenReturn(Optional.empty());

        OfficialPoNumberGenerator generator = new OfficialPoNumberGenerator(shortCodeRepository, sequenceService);

        assertThrows(OfficialPoShortCodeNotConfiguredException.class, () -> generator.generate("SUP_UNKNOWN", "BR_OUTDOOR"));
    }

    @Test
    void throwsWhenBrandShortCodeNotConfigured_neverInventsOne() {
        OfficialPoShortCodeRepository shortCodeRepository = mock(OfficialPoShortCodeRepository.class);
        OfficialPoSequenceService sequenceService = mock(OfficialPoSequenceService.class);
        when(shortCodeRepository.findFirstByCodeTypeAndBusinessCodeAndActiveTrue(eq(OfficialPoShortCode.TYPE_SUPPLIER), eq("SUP_ALPHA")))
                .thenReturn(Optional.of(shortCode(OfficialPoShortCode.TYPE_SUPPLIER, "SUP_ALPHA", "ABC")));
        when(shortCodeRepository.findFirstByCodeTypeAndBusinessCodeAndActiveTrue(eq(OfficialPoShortCode.TYPE_BRAND), eq("BR_UNKNOWN")))
                .thenReturn(Optional.empty());

        OfficialPoNumberGenerator generator = new OfficialPoNumberGenerator(shortCodeRepository, sequenceService);

        assertThrows(OfficialPoShortCodeNotConfiguredException.class, () -> generator.generate("SUP_ALPHA", "BR_UNKNOWN"));
    }
}
