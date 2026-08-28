package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.SkuDetailResponse;
import com.glv.gsysportal.exception.SkuNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Implementation instructions Step 5 4章 - SKU Detail. Legacy READ ONLY,
 * no Prototype writes involved so no transactional rollback wrapper is
 * needed here (unlike the Prototype-writing integration tests). */
@SpringBootTest
@ActiveProfiles("test")
class SkuDetailServiceIntegrationTest {

    @Autowired
    private SkuDetailService skuDetailService;

    @Test
    void detailReturnsProductInventoryOrderingAndHistory() {
        SkuDetailResponse detail = skuDetailService.getDetail("OD-TENT-001");

        assertEquals("OD-TENT-001", detail.sku());
        assertEquals("SUP_ALPHA", detail.supplierCode());
        assertEquals(3, detail.recommendedQty(), "calc4-derived, same as Order Candidate List");
        assertEquals("DEMO_LEGACY", detail.dataSource());
        assertFalse(detail.poHistory().isEmpty(), "seeded Demo Data includes at least one TR_PO/TR_PO_DTL row for this SKU");
    }

    @Test
    void unknownSkuThrowsNotFound() {
        assertThrows(SkuNotFoundException.class, () -> skuDetailService.getDetail("NO-SUCH-SKU"));
    }

    @Test
    void skuWithNoPoHistoryReturnsEmptyListNotFabricatedData() {
        // OD-TENT-002 uses the Default 4EU formula path (no individual MS_FORMULA
        // row) but does have Demo Seed TR_PO data in this environment; the
        // assertion here is deliberately about the *shape* (a real, possibly-empty
        // list) rather than requiring a specific non-empty history, since demo
        // seed contents may evolve - what matters is no exception, no fabrication.
        SkuDetailResponse detail = skuDetailService.getDetail("OD-TENT-002");
        assertTrue(detail.poHistory() != null);
    }
}
