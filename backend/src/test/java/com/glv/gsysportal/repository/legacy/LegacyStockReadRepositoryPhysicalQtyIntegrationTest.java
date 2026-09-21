package com.glv.gsysportal.repository.legacy;

import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
 * gops-stage4-targeted-real-data-remediation.md Remediation A) - proves
 * {@code current_stock} (RecommendedQtyReadQuery.sql) against the
 * dedicated {@code STAGE4-WH-TEST-001} fixture (backend/demo-data/
 * 02-seed.sql), the only item deliberately spanning every relevant WH_CD
 * case at once. Requires the Legacy Demo Instance running with that seed
 * data loaded (docker compose up -d) - same precondition as
 * {@link LegacyReadOnlyIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
class LegacyStockReadRepositoryPhysicalQtyIntegrationTest {

    private static final String SKU = "STAGE4-WH-TEST-001";

    @Autowired
    private LegacyStockReadRepository legacyStockReadRepository;

    private LegacyStockRow fetch() {
        List<LegacyStockRow> rows = legacyStockReadRepository.findBySkus(Set.of(SKU));
        assertEquals(1, rows.size(), "one SKU must produce exactly one Candidate row - no 13x Warehouse-row multiplication");
        return rows.get(0);
    }

    @Test
    void includedWarehousesAreCounted() {
        // WH_CD 4 (10) + 8 (20) + 15 (5) = 35 - also proves multiple
        // physical warehouses are correctly summed, not just one read.
        LegacyStockRow row = fetch();
        assertEquals(35, row.currentStock());
    }

    @Test
    void excludedWarehousesAreNeverCounted() {
        // WH_CD 9/13/14 hold 1000/2000/3000 respectively - if any leaked
        // into the sum, currentStock would be in the thousands, not 35.
        LegacyStockRow row = fetch();
        assertEquals(35, row.currentStock(),
                "WH_CD 9 (Defective) / 13 (Private Auction) / 14 (Disposal) must never contribute to current_stock");
    }

    @Test
    void missingRowForAnIncludedWarehouseCountsAsZero() {
        // WH_CD 12 is PHISICAL_QTY-included (Stage 3B §3) but this fixture
        // has NO row at all for it - the SUM/COALESCE must treat that as 0,
        // not throw, not skip the whole calculation.
        LegacyStockRow row = fetch();
        assertEquals(35, row.currentStock());
    }

    /**
     * Stage 5E Targeted Remediation (RC-C, docs/real-data-audit/
     * gops-stage5e-targeted-remediation.md): proves {@link
     * LegacyStockReadRepository#findBySkus} filters in SQL for a
     * multi-SKU request too, not just the single-SKU case {@link #fetch()}
     * already covers - exactly the requested SKUs come back, nothing else,
     * and nothing is silently dropped. Stage 5D's RCA found the pre-Stage-5E
     * implementation applied NO SQL filter at all here (fetched the entire
     * catalog, filtered in Java) - the confirmed primary cause of SKU
     * Detail's real-Production-scale slowness.
     */
    @Test
    void findBySkusReturnsExactlyTheRequestedSkusForAMultiSkuRequest() {
        List<LegacyStockRow> rows = legacyStockReadRepository.findBySkus(Set.of(SKU, "OD-TENT-001"));
        Set<String> returned = rows.stream().map(LegacyStockRow::itemCd).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(SKU, "OD-TENT-001"), returned);
    }

    /** A request for zero SKUs must short-circuit to an empty result, never
     * fall through to "no filter = everything" (Stage 5D's own root cause). */
    @Test
    void findBySkusWithEmptyCollectionReturnsEmpty() {
        List<LegacyStockRow> rows = legacyStockReadRepository.findBySkus(Set.of());
        assertEquals(0, rows.size());
    }

    @Test
    void logicalQtyBasisExcludesArrQty() {
        // The Strategy-level proof (ARR_QTY never entering the calc) lives
        // in OverseasRecommendedQtyStrategyTest - this asserts the raw
        // fields this fixture's row surfaces are what that test's own
        // reasoning depends on: currentStock=35 (PHISICAL_QTY), openPo=3,
        // openShip=7, openArrival=999 (present in the row, confirmed
        // fetched correctly, but never summed by the Strategy).
        LegacyStockRow row = fetch();
        assertEquals(35, row.currentStock());
        assertEquals(3, row.openPo());
        assertEquals(7, row.openShip());
        assertEquals(999, row.openArrival());
    }
}
