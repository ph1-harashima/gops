package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.dto.response.OfficialPoPreflightIssue;
import com.glv.gsysportal.dto.response.OfficialPoPreflightResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7-C2A 11章/12章. Legacy READ ONLY - no Prototype writes involved, no
 * transactional rollback wrapper needed (mirrors SkuDetailServiceIntegrationTest).
 */
@SpringBootTest
@ActiveProfiles("test")
class OfficialPoPreflightServiceIntegrationTest {

    @Autowired
    private OfficialPoPreflightService preflightService;

    private static PortalOrder order(String supplierCode, String brandCode, String... skus) {
        PortalOrder order = new PortalOrder();
        order.setId(1L);
        order.setSupplierCode(supplierCode);
        order.setBrandCode(brandCode);
        order.setOrderDate(LocalDate.of(2026, 8, 27));
        for (String sku : skus) {
            PortalOrderDetail d = new PortalOrderDetail();
            d.setSku(sku);
            d.setPortalOrder(order);
            order.getDetails().add(d);
        }
        return order;
    }

    @Test
    void knownGoodSupplierBrandAndItemsResultInPass() {
        OfficialPoPreflightResult result = preflightService.run(order("SUP_ALPHA", "BR_OUTDOOR", "OD-TENT-001"));

        assertEquals(OfficialPoPreflightResult.RESULT_PASS, result.result());
        // The always-present informational note (7-C2A 7章's Gate) must still
        // be there even on a clean PASS - it does not downgrade the result.
        assertTrue(result.issues().stream().anyMatch(i ->
                OfficialPoPreflightIssue.SEVERITY_INFO.equals(i.severity())));
        assertTrue(result.issues().stream().noneMatch(i ->
                OfficialPoPreflightIssue.SEVERITY_BLOCKED.equals(i.severity())));
    }

    @Test
    void unknownSupplierIsBlocked() {
        OfficialPoPreflightResult result = preflightService.run(order("SUP_DOES_NOT_EXIST", "BR_OUTDOOR", "OD-TENT-001"));

        assertEquals(OfficialPoPreflightResult.RESULT_BLOCKED, result.result());
        assertTrue(result.issues().stream().anyMatch(i -> "SUPPLIER_NOT_FOUND".equals(i.code())
                && OfficialPoPreflightIssue.SEVERITY_BLOCKED.equals(i.severity())));
    }

    @Test
    void unknownBrandIsBlocked() {
        OfficialPoPreflightResult result = preflightService.run(order("SUP_ALPHA", "BR_DOES_NOT_EXIST", "OD-TENT-001"));

        assertEquals(OfficialPoPreflightResult.RESULT_BLOCKED, result.result());
        assertTrue(result.issues().stream().anyMatch(i -> "BRAND_NOT_FOUND".equals(i.code())));
    }

    @Test
    void unknownItemIsBlockedWithSkuCodeOnTheIssue() {
        OfficialPoPreflightResult result = preflightService.run(order("SUP_ALPHA", "BR_OUTDOOR", "NO-SUCH-SKU"));

        assertEquals(OfficialPoPreflightResult.RESULT_BLOCKED, result.result());
        assertTrue(result.issues().stream().anyMatch(i -> "ITEM_NOT_FOUND".equals(i.code())
                && "NO-SUCH-SKU".equals(i.skuCode())));
    }

    @Test
    void mixOfKnownAndUnknownItemsReportsOnlyTheUnknownOne() {
        OfficialPoPreflightResult result = preflightService.run(
                order("SUP_ALPHA", "BR_OUTDOOR", "OD-TENT-001", "NO-SUCH-SKU"));

        List<OfficialPoPreflightIssue> itemIssues = result.issues().stream()
                .filter(i -> "ITEM_NOT_FOUND".equals(i.code())).toList();
        assertEquals(1, itemIssues.size());
        assertEquals("NO-SUCH-SKU", itemIssues.get(0).skuCode());
    }
}
