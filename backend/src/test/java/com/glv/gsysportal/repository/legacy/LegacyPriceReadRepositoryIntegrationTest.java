package com.glv.gsysportal.repository.legacy;

import com.glv.gsysportal.repository.legacy.row.LegacyPriceRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 8-B: Current Price / Cost read against real Legacy Demo MySQL Seed
 * Data (backend/demo-data/02-seed.sql). Legacy READ ONLY throughout - this
 * class only ever calls {@link LegacyPriceReadRepository}'s query methods.
 */
@SpringBootTest
@ActiveProfiles("test")
class LegacyPriceReadRepositoryIntegrationTest {

    @Autowired
    private LegacyPriceReadRepository repository;

    @Test
    void findBySkusReturnsSeededPriceAndCost() {
        List<LegacyPriceRow> rows = repository.findBySkus(List.of("OD-TENT-001"));

        assertEquals(1, rows.size());
        LegacyPriceRow row = rows.get(0);
        assertEquals("OD-TENT-001", row.itemCd());
        assertEquals("BR_OUTDOOR", row.brandCd());
        assertEquals("FIELDNEST", row.brandName());
        assertEquals("IG-OD-TENT", row.itemGrpCd());
        assertEquals(0, new BigDecimal("1930.00").compareTo(row.prcSellWTax()));
        assertEquals(0, new BigDecimal("1137.00").compareTo(row.costThisMonthAvg()));
        assertTrue(row.freeShipFlg());
        assertEquals(0, new BigDecimal("300.00").compareTo(row.shipFee()));
    }

    @Test
    void findBySkusIgnoresUnknownSkusRatherThanThrowing() {
        List<LegacyPriceRow> rows = repository.findBySkus(List.of("OD-TENT-001", "DOES-NOT-EXIST"));

        assertEquals(1, rows.size());
        assertEquals("OD-TENT-001", rows.get(0).itemCd());
    }

    @Test
    void searchByItemGroupReturnsBothSkusInThatGroup() {
        List<LegacyPriceRow> rows = repository.search(null, "IG-OD-TENT", null);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> r.itemCd().equals("OD-TENT-001")));
        assertTrue(rows.stream().anyMatch(r -> r.itemCd().equals("OD-TENT-002")));
    }

    @Test
    void searchByBrandFiltersCorrectly() {
        List<LegacyPriceRow> rows = repository.search("BR_KITCHEN", null, null);

        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(r -> "BR_KITCHEN".equals(r.brandCd())));
    }

    @Test
    void searchByKeywordMatchesItemCdOrDescription() {
        List<LegacyPriceRow> rows = repository.search(null, null, "KT-KNIFE-001");

        assertEquals(1, rows.size());
        assertEquals("KT-KNIFE-001", rows.get(0).itemCd());
    }

    @Test
    void negativeMarginFixtureIsReadableAsIs() {
        // KT-BOWL-002 is deliberately seeded with prcSellWTax BELOW cost -
        // the Repository must not filter, clamp, or otherwise "fix" this,
        // Phase 8-B forbids attaching any threshold judgment to a raw read.
        Optional<LegacyPriceRow> row = repository.findBySkus(List.of("KT-BOWL-002")).stream().findFirst();

        assertTrue(row.isPresent());
        assertEquals(0, new BigDecimal("3500.00").compareTo(row.get().prcSellWTax()));
        assertEquals(0, new BigDecimal("3603.00").compareTo(row.get().costThisMonthAvg()));
    }

    @Test
    void findDistinctItemGroupCodesIncludesSeededGroups() {
        List<String> groups = repository.findDistinctItemGroupCodes();

        assertTrue(groups.contains("IG-OD-TENT"));
        assertTrue(groups.contains("IG-KT-BOWL"));
        // OD-LAMP-001 is deliberately seeded with a NULL item_grp_cd - must
        // never appear as a group code itself.
        assertFalse(groups.contains(null));
    }
}
