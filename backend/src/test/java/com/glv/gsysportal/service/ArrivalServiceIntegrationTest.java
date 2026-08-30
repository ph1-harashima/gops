package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.ArrivalDetailResponse;
import com.glv.gsysportal.dto.response.ArrivalLineView;
import com.glv.gsysportal.dto.response.ArrivalSummaryResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.exception.ArrivalNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 8-G 20章 (Arrival minimum Test list): PO/Invoice/Arrival Join,
 * List, Detail, Filter, Pagination, Quantity fields. Legacy READ ONLY is
 * covered separately by {@link com.glv.gsysportal.repository.legacy.LegacyReadOnlyIntegrationTest}. */
@SpringBootTest
@ActiveProfiles("test")
class ArrivalServiceIntegrationTest {

    @Autowired
    private ArrivalService arrivalService;

    @Test
    void listReturnsEverySeededArrivalWithDefaultPaging() {
        PageResponse<ArrivalSummaryResponse> page = arrivalService.list(
                null, null, null, null, null, null, null, null, null);
        assertEquals(5, page.totalElements(), "backend/demo-data/02-seed.sql seeds exactly 5 tr_arr rows this Phase");
        assertEquals(0, page.page());
        assertEquals(ArrivalService.DEFAULT_PAGE_SIZE, page.size());
        assertTrue(page.content().size() <= page.size());
    }

    @Test
    void filterBySupplierCodeNarrowsResults() {
        PageResponse<ArrivalSummaryResponse> page = arrivalService.list(
                "SUP_ALPHA", null, null, null, null, null, null, null, null);
        assertTrue(page.content().stream().allMatch(r -> "SUP_ALPHA".equals(r.supplierCode())));
        assertEquals(2, page.totalElements(), "PO-OUTDOOR-01/02 are the only SUP_ALPHA Arrivals seeded");
    }

    @Test
    void filterByBrandCodeNarrowsResults() {
        PageResponse<ArrivalSummaryResponse> page = arrivalService.list(
                null, "BR_KITCHEN", null, null, null, null, null, null, null);
        assertEquals(1, page.totalElements());
        assertEquals("PO-KITCHEN-14", page.content().get(0).poNumber());
    }

    @Test
    void filterByPoNumberNarrowsResults() {
        PageResponse<ArrivalSummaryResponse> page = arrivalService.list(
                null, null, "PO-OUTDOOR-01", null, null, null, null, null, null);
        assertEquals(1, page.totalElements());
        assertEquals("INV-OUTDOOR-01", page.content().get(0).invoiceNumber());
    }

    @Test
    void filterBySkuKeywordMatchesInvoiceLineItem() {
        PageResponse<ArrivalSummaryResponse> page = arrivalService.list(
                null, null, null, null, "HM-MUG-001", null, null, null, null);
        assertEquals(1, page.totalElements());
        assertEquals("PO-HOME-08", page.content().get(0).poNumber());
    }

    @Test
    void filterByArrivalDateRangeNarrowsResults() {
        // Every seeded ETA is in July 2026 except PO-HOME-08 (2026-09-05).
        PageResponse<ArrivalSummaryResponse> julyOnly = arrivalService.list(
                null, null, null, null, null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null, null);
        assertEquals(4, julyOnly.totalElements());
        assertTrue(julyOnly.content().stream().noneMatch(r -> "PO-HOME-08".equals(r.poNumber())));
    }

    @Test
    void paginationReturnsDistinctPagesCoveringEveryRow() {
        PageResponse<ArrivalSummaryResponse> firstPage = arrivalService.list(
                null, null, null, null, null, null, null, 0, 2);
        PageResponse<ArrivalSummaryResponse> secondPage = arrivalService.list(
                null, null, null, null, null, null, null, 1, 2);
        assertEquals(2, firstPage.content().size());
        assertEquals(2, secondPage.content().size());
        assertEquals(5, firstPage.totalElements());
        assertEquals(3, firstPage.totalPages());
        List<String> firstPagePoNumbers = firstPage.content().stream().map(ArrivalSummaryResponse::poNumber).toList();
        List<String> secondPagePoNumbers = secondPage.content().stream().map(ArrivalSummaryResponse::poNumber).toList();
        assertTrue(firstPagePoNumbers.stream().noneMatch(secondPagePoNumbers::contains), "pages must not overlap");
    }

    @Test
    void pageSizeIsClampedToMaximum() {
        PageResponse<ArrivalSummaryResponse> page = arrivalService.list(
                null, null, null, null, null, null, null, 0, 999999);
        assertEquals(ArrivalService.MAX_PAGE_SIZE, page.size());
    }

    @Test
    void fullyStockedInArrivalShowsMatchingQuantitiesAtEveryStage() {
        ArrivalDetailResponse detail = arrivalService.detail("SUP_ALPHA", "PO-OUTDOOR-01", "INV-OUTDOOR-01");
        ArrivalSummaryResponse header = detail.header();
        assertEquals(3, header.orderedQty());
        assertEquals(3, header.invoiceQty());
        assertEquals(3, header.arrivalQty(), "tr_arr.qty - a separate header-level Source Fact, not recomputed");
        assertEquals(3, header.stockInQty());
        assertEquals("BL-OUTDOOR-001", header.blNumber());
        assertEquals(1, detail.lines().size());
        assertEquals("OD-TENT-001", detail.lines().get(0).sku());
    }

    @Test
    void creditNettedArrivalShowsTrueStockInQtyAtBothHeaderAndLineLevel() {
        // PO-OUTDOOR-05: invoiced 3, but the Credit PO/Invoice pair nets
        // qty_stk_in down to 3 + (-1) = 2 (Phase 7-C7A's exact mechanism,
        // reused unmodified here).
        ArrivalDetailResponse detail = arrivalService.detail("SUP_BETA", "PO-OUTDOOR-05", "INV-OUTDOOR-05");
        assertEquals(3, detail.header().invoiceQty());
        assertEquals(2, detail.header().stockInQty(), "Original(3) + Credit(-1) = 2, netted the same way Fulfillment does");
        ArrivalLineView line = detail.lines().get(0);
        assertEquals(3, line.invoiceQty());
        assertEquals(2, line.stockInQty());
    }

    @Test
    void inTransitArrivalLeavesStockInQtyNull() {
        ArrivalDetailResponse detail = arrivalService.detail("SUP_GAMMA", "PO-HOME-08", "INV-HOME-08");
        assertEquals(3, detail.header().invoiceQty());
        assertEquals(null, detail.header().stockInQty(), "qty_stk_in is NULL in Source - must stay null, never fabricated as 0");
        assertEquals(null, detail.header().etaWarehouse());
        assertEquals(null, detail.header().stockInDate());
    }

    @Test
    void unknownArrivalKeyThrowsNotFound() {
        assertThrows(ArrivalNotFoundException.class,
                () -> arrivalService.detail("SUP_ALPHA", "NO-SUCH-PO", "NO-SUCH-INV"));
    }

    @Test
    void poWithNoInvoiceOrArrivalYetIsAbsentFromList() {
        // PO-OUTDOOR-03 deliberately has no tr_inv/tr_arr row (backend/
        // demo-data/02-seed.sql) - filtering the List by it must return an
        // honest empty result, matching the "入荷情報を見る" link's target
        // state (Phase 8-G 12章), not a fabricated row.
        PageResponse<ArrivalSummaryResponse> page = arrivalService.list(
                null, null, "PO-OUTDOOR-03", null, null, null, null, null, null);
        assertEquals(0, page.totalElements());
        assertTrue(page.content().isEmpty());
    }
}
