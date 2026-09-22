package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.WarehouseStockDetailResponse;
import com.glv.gsysportal.dto.response.WarehouseStockSummaryResponse;
import com.glv.gsysportal.exception.SkuNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 8-G 20章 (Warehouse minimum Test list): Warehouse Stock List, SKU
 * Filter, Brand Filter, WH_CD Filter, Pagination, Current Stock Qty. */
@SpringBootTest
@ActiveProfiles("test")
class WarehouseStockServiceIntegrationTest {

    @Autowired
    private WarehouseStockService warehouseStockService;

    @Test
    void listExcludesTheXxAggregateRow() {
        PageResponse<WarehouseStockSummaryResponse> page = warehouseStockService.list(
                null, null, null, null, null, 0, 200);
        assertTrue(page.content().stream().noneMatch(r -> "XX".equals(r.warehouseCode())),
                "'XX' is Legacy's own aggregate row, never a real physical warehouse (Phase 8-G 8章)");
    }

    @Test
    void filterBySkuKeywordMatchesItemCodeOrName() {
        PageResponse<WarehouseStockSummaryResponse> page = warehouseStockService.list(
                "OD-TENT-001", null, null, null, null, null, null);
        assertTrue(page.content().stream().allMatch(r -> "OD-TENT-001".equals(r.sku())));
        assertTrue(page.totalElements() >= 3, "OD-TENT-001 has wh_cd 01/04/05 seeded this Phase");
    }

    @Test
    void filterByBrandCodeNarrowsResults() {
        PageResponse<WarehouseStockSummaryResponse> page = warehouseStockService.list(
                null, "BR_KITCHEN", null, null, null, 0, 200);
        assertTrue(page.content().stream().allMatch(r -> "BR_KITCHEN".equals(r.brandCode())));
    }

    @Test
    void filterByWarehouseCodeNarrowsResults() {
        PageResponse<WarehouseStockSummaryResponse> page = warehouseStockService.list(
                null, null, "04", null, null, 0, 200);
        assertTrue(page.content().stream().allMatch(r -> "04".equals(r.warehouseCode())));
        assertEquals(3, page.totalElements(), "wh_cd='04' is seeded for OD-TENT-001/OD-CHAIR-001/KT-PAN-001 this Phase");
    }

    @Test
    void filterByQtyRangeIsAPlainNumericFilterNotABusinessJudgment() {
        PageResponse<WarehouseStockSummaryResponse> page = warehouseStockService.list(
                null, null, null, 5, null, 0, 200);
        assertTrue(page.content().stream().allMatch(r -> r.stockQty() != null && r.stockQty() >= 5));
    }

    @Test
    void paginationReturnsDistinctPagesCoveringEveryRow() {
        long total = warehouseStockService.list(null, null, null, null, null, 0, 200).totalElements();
        PageResponse<WarehouseStockSummaryResponse> firstPage = warehouseStockService.list(
                null, null, null, null, null, 0, 10);
        assertEquals(10, firstPage.content().size());
        assertEquals(total, firstPage.totalElements());
        assertEquals((int) Math.ceil(total / 10.0), firstPage.totalPages());
    }

    @Test
    void pageSizeIsClampedToMaximum() {
        PageResponse<WarehouseStockSummaryResponse> page = warehouseStockService.list(
                null, null, null, null, null, 0, 999999);
        assertEquals(WarehouseStockService.MAX_PAGE_SIZE, page.size());
    }

    @Test
    void detailReturnsEveryPhysicalWarehouseRowForOneSku() {
        WarehouseStockDetailResponse detail = warehouseStockService.detail("OD-TENT-001");
        assertEquals("OD-TENT-001", detail.sku());
        assertEquals("BR_OUTDOOR", detail.brandCode());
        assertTrue(detail.warehouses().stream().noneMatch(w -> "XX".equals(w.warehouseCode())));
        assertTrue(detail.warehouses().size() >= 3, "wh_cd 01/04/05 seeded for this SKU this Phase");
    }

    @Test
    void unknownSkuThrowsNotFound() {
        assertThrows(SkuNotFoundException.class, () -> warehouseStockService.detail("NO-SUCH-SKU"));
    }

    // ========================================================================
    // Warehouse Stock Production-Scale Remediation (docs/real-data-audit/
    // gops-warehouse-stock-production-scale-remediation.md §9): the two-step
    // fetch (page keys, then detail-by-item-codes, reassembled in Java) must
    // never change what a page actually contains - every test below proves
    // exact parity with the pre-remediation single-query semantics, at the
    // WarehouseStockService level (the same public entry point the old and
    // new SQL both sit behind).
    // ========================================================================

    /** "size=20 means 20 Warehouse Stock rows, not 20 SKUs" - the
     * remediation doc §3's own explicit requirement. OD-TENT-001 alone has
     * 4 physical-warehouse rows this Phase (01/04/05/6) - a page size of 2
     * scoped to just this one SKU must split its own warehouse rows across
     * two pages, never round up/down to "the whole SKU" on one page. */
    @Test
    void pageSizeCountsWarehouseRowsNotSkus() {
        PageResponse<WarehouseStockSummaryResponse> allRowsForSku = warehouseStockService.list(
                "OD-TENT-001", null, null, null, null, 0, 200);
        assertTrue(allRowsForSku.totalElements() >= 4, "OD-TENT-001 has wh_cd 01/04/05/6 seeded this Phase");
        assertTrue(allRowsForSku.content().stream().allMatch(r -> "OD-TENT-001".equals(r.sku())));

        PageResponse<WarehouseStockSummaryResponse> firstPage = warehouseStockService.list(
                "OD-TENT-001", null, null, null, null, 0, 2);
        assertEquals(2, firstPage.content().size(), "page size 2 must return exactly 2 warehouse rows, not round up to the whole SKU");
        assertTrue(firstPage.content().stream().allMatch(r -> "OD-TENT-001".equals(r.sku())));
    }

    /** Page boundary falling INSIDE one SKU's own warehouse rows - the
     * remediation doc §3/§9's most important edge case, since the old
     * single-query design and the new two-step design both paginate at the
     * (item_cd, wh_cd) row level, never the SKU level. */
    @Test
    void pageBoundaryInsideOneSkusWarehouseRowsHasNoDuplicateOrMissingRow() {
        List<WarehouseStockSummaryResponse> reference = warehouseStockService.list(
                "OD-TENT-001", null, null, null, null, 0, 200).content();
        assertTrue(reference.size() >= 4);

        List<WarehouseStockSummaryResponse> page0 = warehouseStockService.list(
                "OD-TENT-001", null, null, null, null, 0, 2).content();
        List<WarehouseStockSummaryResponse> page1 = warehouseStockService.list(
                "OD-TENT-001", null, null, null, null, 1, 2).content();

        List<WarehouseStockSummaryResponse> concatenated = new ArrayList<>(page0);
        concatenated.addAll(page1);
        // reference may have more than 4 rows if seed data grows later - only
        // compare the first 4 (what page0+page1 together cover) to stay robust.
        assertEquals(reference.subList(0, 4), concatenated.subList(0, Math.min(4, concatenated.size())),
                "page0 + page1, concatenated, must exactly equal the same prefix of the unpaginated reference order - "
                        + "no row skipped, no row duplicated, across a boundary that falls inside one SKU's own warehouse rows");
        Set<String> pairKeys = concatenated.stream().map(r -> r.warehouseCode() + "/" + r.sku()).collect(Collectors.toSet());
        assertEquals(concatenated.size(), pairKeys.size(), "no (item_cd, wh_cd) pair may appear twice across adjacent pages");
    }

    /** Multiple SKUs within one Brand, multiple Brands overall - proves the
     * two-step fetch's Step 2 (item_cd-scoped superset, filtered back down
     * in Java) correctly handles more than one distinct item_cd on a single
     * page, not just the single-SKU case above. */
    @Test
    void pageSpanningMultipleSkusWithinABrandStaysCorrectlyOrderedAndComplete() {
        List<WarehouseStockSummaryResponse> reference = warehouseStockService.list(
                null, "BR_OUTDOOR", null, null, null, 0, 200).content();
        assertTrue(reference.stream().map(WarehouseStockSummaryResponse::sku).collect(Collectors.toSet()).size() >= 2,
                "BR_OUTDOOR must have more than one distinct SKU seeded this Phase");

        List<WarehouseStockSummaryResponse> reassembled = new ArrayList<>();
        int pageSize = 3;
        for (int page = 0; page * pageSize < reference.size(); page++) {
            reassembled.addAll(warehouseStockService.list(null, "BR_OUTDOOR", null, null, null, page, pageSize).content());
        }
        assertEquals(reference, reassembled,
                "reassembling every page (size 3) must reproduce the exact unpaginated reference order, "
                        + "across multiple SKUs and their own multiple warehouse rows, with no gap or duplicate");
    }

    /** Multiple Brands overall (no Brand filter) - the default/unfiltered
     * landing state that reproduced the original Production-scale defect
     * (docs/real-data-audit/gops-warehouse-stock-production-scale-performance-rca.md) -
     * same full-reassembly proof, at Demo scale, across every Brand. */
    @Test
    void unfilteredMultiBrandPaginationStaysCorrectlyOrderedAndComplete() {
        List<WarehouseStockSummaryResponse> reference = warehouseStockService.list(
                null, null, null, null, null, 0, 200).content();
        assertTrue(reference.stream().map(WarehouseStockSummaryResponse::brandCode).collect(Collectors.toSet()).size() > 1,
                "the Demo fixture must span more than one Brand for this test to be meaningful");

        List<WarehouseStockSummaryResponse> reassembled = new ArrayList<>();
        int pageSize = 5;
        for (int page = 0; page * pageSize < reference.size(); page++) {
            reassembled.addAll(warehouseStockService.list(null, null, null, null, null, page, pageSize).content());
        }
        assertEquals(reference, reassembled);
    }

    /** Filter + pagination together - proves filter predicates are applied
     * in Step 1 (the page-key query), not after paging, per the remediation
     * doc §6's explicit "do not page first, then filter" requirement:
     * a filtered, paginated result must be a subsequence of the SAME
     * filter's unpaginated result, at the exact same relative positions. */
    @Test
    void filterAppliesBeforePaginationNotAfter() {
        List<WarehouseStockSummaryResponse> fullFiltered = warehouseStockService.list(
                null, "BR_HOME", null, null, null, 0, 200).content();
        assertTrue(fullFiltered.size() >= 3, "BR_HOME must have at least 3 physical-warehouse rows seeded this Phase");

        List<WarehouseStockSummaryResponse> page0 = warehouseStockService.list(
                null, "BR_HOME", null, null, null, 0, 2).content();
        assertEquals(fullFiltered.subList(0, 2), page0);
        assertTrue(page0.stream().allMatch(r -> "BR_HOME".equals(r.brandCode())));
    }

    /** Stable ordering, explicitly - repeated identical requests must
     * return byte-for-byte the same order (no ORDER-BY-less nondeterminism
     * introduced by the two-step split). */
    @Test
    void repeatedRequestsReturnIdenticallyOrderedResults() {
        List<WarehouseStockSummaryResponse> first = warehouseStockService.list(
                null, null, null, null, null, 0, 50).content();
        List<WarehouseStockSummaryResponse> second = warehouseStockService.list(
                null, null, null, null, null, 0, 50).content();
        assertEquals(first, second);
    }
}
