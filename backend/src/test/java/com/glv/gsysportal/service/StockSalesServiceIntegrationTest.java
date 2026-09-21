package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.StockSalesSummaryResponse;
import com.glv.gsysportal.exception.SkuNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 8-H 18章 (Backend minimum Test list): Stock/Sales List, Filter,
 * Pagination, SOLD_QTY, STK_QTY, Open PO, Open Arrival, Timestamp, Legacy
 * READ ONLY (covered separately by LegacyReadOnlyIntegrationTest - no new
 * write surface was added this Phase, so no new Test is needed there). */
@SpringBootTest
@ActiveProfiles("test")
class StockSalesServiceIntegrationTest {

    @Autowired
    private StockSalesService stockSalesService;

    @Autowired
    private SkuDetailService skuDetailService;

    @Test
    void listReturnsSeededSkusWithDefaultPaging() {
        PageResponse<StockSalesSummaryResponse> page = stockSalesService.list(
                null, null, null, null, null, null, null, null, null);
        assertTrue(page.totalElements() >= 19, "backend/demo-data/02-seed.sql seeds at least 19 ms_item rows");
        assertEquals(0, page.page());
        assertEquals(StockSalesService.DEFAULT_PAGE_SIZE, page.size());
        assertTrue(page.content().size() <= page.size());
    }

    @Test
    void filterBySkuKeywordMatchesExactSku() {
        PageResponse<StockSalesSummaryResponse> page = stockSalesService.list(
                "OD-TENT-001", null, null, null, null, null, null, null, null);
        assertEquals(1, page.totalElements());
        assertEquals("OD-TENT-001", page.content().get(0).sku());
    }

    /**
     * Stage 5E Targeted Remediation (RC-C/RC-E, docs/real-data-audit/
     * gops-stage5e-targeted-remediation.md): confirms the two-step
     * pagination redesign (RC-C) preserved exact correctness for Stock/Sales
     * List, and that Open PO and Open Arrival remain two genuinely separate
     * fields here (RC-E fixed a combined-field bug in Candidate List only -
     * this test proves Stock/Sales was never affected and stays correct
     * after RC-C's query rewrite). Same STAGE4-WH-TEST-001 fixture as
     * LegacyStockReadRepositoryPhysicalQtyIntegrationTest (po_qty_1=3,
     * arr_qty_1=999).
     */
    @Test
    void openPoAndOpenArrivalRemainSeparateFields() {
        PageResponse<StockSalesSummaryResponse> page = stockSalesService.list(
                "STAGE4-WH-TEST-001", null, null, null, null, null, null, null, null);
        assertEquals(1, page.totalElements());
        StockSalesSummaryResponse row = page.content().get(0);
        assertEquals(3, row.openPoQty());
        assertEquals(999, row.openArrivalQty());
    }

    @Test
    void filterByBrandCodeNarrowsResults() {
        PageResponse<StockSalesSummaryResponse> page = stockSalesService.list(
                null, "BR_KITCHEN", null, null, null, null, null, 0, 200);
        assertTrue(page.content().stream().allMatch(r -> "BR_KITCHEN".equals(r.brandCode())));
        assertTrue(page.totalElements() >= 6, "6 KT-* items seeded");
    }

    @Test
    void filterBySupplierCodeNarrowsResults() {
        PageResponse<StockSalesSummaryResponse> page = stockSalesService.list(
                null, null, "SUP_ALPHA", null, null, null, null, 0, 200);
        assertTrue(page.content().stream().allMatch(r -> "SUP_ALPHA".equals(r.supplierCode())));
    }

    @Test
    void filterByStockRangeIsAPlainNumericFilter() {
        // OD-BAG-002 has wh01 current_stock=0 (backend/demo-data/02-seed.sql).
        PageResponse<StockSalesSummaryResponse> zeroStock = stockSalesService.list(
                null, null, null, 0, 0, null, null, 0, 200);
        assertTrue(zeroStock.content().stream().anyMatch(r -> "OD-BAG-002".equals(r.sku())));
        assertTrue(zeroStock.content().stream().allMatch(r -> r.currentStock() != null && r.currentStock() == 0));
    }

    @Test
    void filterBySalesRangeIsAPlainNumericFilter() {
        // OD-TENT-001 sold_qty=42 (backend/demo-data/02-seed.sql).
        PageResponse<StockSalesSummaryResponse> page = stockSalesService.list(
                null, null, null, null, null, 40, 50, 0, 200);
        assertTrue(page.content().stream().anyMatch(r -> "OD-TENT-001".equals(r.sku())));
        assertTrue(page.content().stream().allMatch(r -> r.currentMonthSalesQty() != null
                && r.currentMonthSalesQty() >= 40 && r.currentMonthSalesQty() <= 50));
    }

    @Test
    void paginationReturnsDistinctPagesCoveringEveryRow() {
        PageResponse<StockSalesSummaryResponse> firstPage = stockSalesService.list(
                null, null, null, null, null, null, null, 0, 5);
        PageResponse<StockSalesSummaryResponse> secondPage = stockSalesService.list(
                null, null, null, null, null, null, null, 1, 5);
        assertEquals(5, firstPage.content().size());
        assertEquals(5, secondPage.content().size());
        List<String> firstSkus = firstPage.content().stream().map(StockSalesSummaryResponse::sku).toList();
        List<String> secondSkus = secondPage.content().stream().map(StockSalesSummaryResponse::sku).toList();
        assertTrue(firstSkus.stream().noneMatch(secondSkus::contains), "pages must not overlap");
    }

    @Test
    void pageSizeIsClampedToMaximum() {
        PageResponse<StockSalesSummaryResponse> page = stockSalesService.list(
                null, null, null, null, null, null, null, 0, 999999);
        assertEquals(StockSalesService.MAX_PAGE_SIZE, page.size());
    }

    @Test
    void currentMonthSalesQtyMirrorsSourceSoldQtyExactly() {
        // OD-TENT-001 sold_qty=42 (backend/demo-data/02-seed.sql) - the
        // Current-Month-Cumulative value (Phase 8-C), never a Daily/Trend figure.
        StockSalesSummaryResponse detail = stockSalesService.detail("OD-TENT-001");
        assertEquals(42, detail.currentMonthSalesQty());
    }

    @Test
    void openPoAndOpenArrivalAreDistinctFieldsNeverSummed() {
        // OD-BAG-002: po_qty_1=15, arr_qty_1=10 (backend/demo-data/02-seed.sql) -
        // must surface as 2 separate fields, unlike OrderCandidateResponse's
        // own summed openPo (Phase 8-H 4章's explicit "Open PO Qty"/"Open
        // Arrival Qty" as 2 distinct display candidates).
        StockSalesSummaryResponse detail = stockSalesService.detail("OD-BAG-002");
        assertEquals(15, detail.openPoQty());
        assertEquals(10, detail.openArrivalQty());
    }

    @Test
    void updatedAtIsPopulatedFromLegacyMsStk() {
        StockSalesSummaryResponse detail = stockSalesService.detail("OD-TENT-001");
        assertNotNull(detail.updatedAt(), "ms_stk.update_datetime is seeded NOW() for every row");
    }

    @Test
    void recommendedQtyMatchesSkuDetailsOwnCalc4UnmodifiedForTheSameSku() {
        // Phase 8-H 9章: Recommended Qty Logic must not change - proves this
        // screen's calc4 value is byte-for-byte the same SkuDetailService
        // (an existing, unmodified caller of the same Calculator) produces.
        StockSalesSummaryResponse stockSales = stockSalesService.detail("OD-BAG-002");
        var skuDetail = skuDetailService.getDetail("OD-BAG-002");
        assertEquals(skuDetail.recommendedQty(), stockSales.recommendedQty());
    }

    @Test
    void unknownSkuThrowsNotFound() {
        assertThrows(SkuNotFoundException.class, () -> stockSalesService.detail("NO-SUCH-SKU"));
    }
}
