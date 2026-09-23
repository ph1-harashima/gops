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

/** BR-08 (docs/gulliver-20260917-confirmed-business-rules.md) as reconciled
 * by docs/ux-audit/gops-official-po-number-final-reality-audit.md: pure
 * unit test for the Legacy-compatible composition rule
 * ({@code #{SupplierShortCode}-{BrandShortCode}{3-digit sequence}}) and the
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

    private static OfficialPoNumberGenerator generatorWith(String supplierBusinessCode, String supplierShort,
                                                             String brandBusinessCode, String brandShort, int seq) {
        OfficialPoShortCodeRepository shortCodeRepository = mock(OfficialPoShortCodeRepository.class);
        OfficialPoSequenceService sequenceService = mock(OfficialPoSequenceService.class);
        when(shortCodeRepository.findFirstByCodeTypeAndBusinessCodeAndActiveTrue(eq(OfficialPoShortCode.TYPE_SUPPLIER), eq(supplierBusinessCode)))
                .thenReturn(Optional.of(shortCode(OfficialPoShortCode.TYPE_SUPPLIER, supplierBusinessCode, supplierShort)));
        when(shortCodeRepository.findFirstByCodeTypeAndBusinessCodeAndActiveTrue(eq(OfficialPoShortCode.TYPE_BRAND), eq(brandBusinessCode)))
                .thenReturn(Optional.of(shortCode(OfficialPoShortCode.TYPE_BRAND, brandBusinessCode, brandShort)));
        when(sequenceService.nextSequence(supplierBusinessCode, brandBusinessCode)).thenReturn(seq);
        return new OfficialPoNumberGenerator(shortCodeRepository, sequenceService);
    }

    @Test
    void composesHashSupplierShortCodeDashBrandShortCodePlusThreeDigitSequence() {
        OfficialPoNumberGenerator generator = generatorWith("SUP_ALPHA", "ABC", "BR_OUTDOOR", "DEF", 1);

        assertEquals("#ABC-DEF001", generator.generate("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void padsTheSequenceToThreeDigits() {
        OfficialPoNumberGenerator generator = generatorWith("SUP_ALPHA", "ABC", "BR_OUTDOOR", "DEF", 2);

        assertEquals("#ABC-DEF002", generator.generate("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void aDifferentBrandChangesOnlyTheBrandSegment() {
        OfficialPoNumberGenerator generator = generatorWith("SUP_ALPHA", "ABC", "BR_OUTDOOR", "XYZ", 1);

        assertEquals("#ABC-XYZ001", generator.generate("SUP_ALPHA", "BR_OUTDOOR"));
    }

    @Test
    void aDifferentSupplierChangesOnlyTheSupplierSegment() {
        OfficialPoNumberGenerator generator = generatorWith("SUP_ZETA", "ZZZ", "BR_OUTDOOR", "DEF", 1);

        assertEquals("#ZZZ-DEF001", generator.generate("SUP_ZETA", "BR_OUTDOOR"));
    }

    /** docs/ux-audit/gops-official-po-number-final-reality-audit.md §3/§12:
     * a faithful re-implementation of Legacy's real {@code
     * BusinessLogicUtil.getSupplierCd/getBrandCd/getIdCd} substring logic
     * (0-indexed {@code substring(0,4)}/{@code substring(5,8)}/{@code
     * substring(9,11)}), proving the number this class now generates
     * parses back into exactly the same Supplier/Brand segments a real
     * Legacy Import would extract - the entire point of this Phase's fix. */
    @Test
    void generatedNumberParsesCorrectlyThroughTheRealLegacyParserLogic() {
        OfficialPoNumberGenerator generator = generatorWith("SUP_ALPHA", "ABC", "BR_OUTDOOR", "DEF", 1);
        String poNo = generator.generate("SUP_ALPHA", "BR_OUTDOOR");

        assertEquals("#ABC-DEF001", poNo);
        assertEquals(11, poNo.length(), "Legacy's getIdCd() requires length >= 11 - this must clear that gate");
        assertEquals("#ABC", legacyGetSupplierCd(poNo), "must match the real Legacy Supplier Master code shape (# + 3 chars)");
        assertEquals("DEF", legacyGetBrandCd(poNo), "must match the real Legacy Brand Master code shape (3 chars)");
    }

    @Test
    void generatedNumberParsesCorrectlyAcrossDifferentSupplierAndBrandCombinations() {
        assertEquals("#ZZZ-DEF001", generatorWith("S", "ZZZ", "B", "DEF", 1).generate("S", "B"));
        String poNo = generatorWith("S", "ZZZ", "B", "XYZ", 42).generate("S", "B");
        assertEquals("#ZZZ-XYZ042", poNo);
        assertEquals("#ZZZ", legacyGetSupplierCd(poNo));
        assertEquals("XYZ", legacyGetBrandCd(poNo));
    }

    /** Faithful port of {@code BusinessLogicUtil.getSupplierCd} (0-indexed
     * {@code poNo.substring(0,4).toUpperCase()}, null-guarded on length < 4). */
    private static String legacyGetSupplierCd(String poNo) {
        if (poNo == null || poNo.length() < 4) return null;
        return poNo.substring(0, 4).toUpperCase();
    }

    /** Faithful port of {@code BusinessLogicUtil.getBrandCd} (0-indexed
     * {@code poNo.substring(5,8).toUpperCase()}, null-guarded on length < 8). */
    private static String legacyGetBrandCd(String poNo) {
        if (poNo == null || poNo.length() < 8) return null;
        return poNo.substring(5, 8).toUpperCase();
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
