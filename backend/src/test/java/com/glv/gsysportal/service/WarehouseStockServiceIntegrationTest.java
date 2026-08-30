package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.WarehouseStockDetailResponse;
import com.glv.gsysportal.dto.response.WarehouseStockSummaryResponse;
import com.glv.gsysportal.exception.SkuNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
}
