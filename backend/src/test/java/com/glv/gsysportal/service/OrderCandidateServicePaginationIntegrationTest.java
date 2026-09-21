package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 4 Targeted Real-Data Remediation (Remediation C, docs/real-data-audit/
 * gops-stage4-targeted-real-data-remediation.md) - GET /api/order-candidates
 * previously returned every matching row unpaginated (a confirmed real
 * Brand has 18,596 SKUs, Stage 2 §5). Mirrors
 * StockSalesServiceIntegrationTest's own pagination test shape - same
 * Backend, same MAX/DEFAULT_PAGE_SIZE contract.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderCandidateServicePaginationIntegrationTest {

    @Autowired
    private OrderCandidateService orderCandidateService;

    @Test
    void defaultPagingReturnsFirstPage() {
        PageResponse<OrderCandidateResponse> page = orderCandidateService.findOrderCandidatesPage(null, null, null, null, null);
        assertEquals(0, page.page());
        assertEquals(OrderCandidateService.DEFAULT_PAGE_SIZE, page.size());
        assertTrue(page.content().size() <= page.size());
        assertTrue(page.totalElements() >= 19, "backend/demo-data/02-seed.sql seeds at least 19 ms_item rows");
    }

    @Test
    void firstMiddleAndLastPageCoverDistinctRowsWithNoOverlap() {
        int size = 5;
        PageResponse<OrderCandidateResponse> first = orderCandidateService.findOrderCandidatesPage(null, null, null, 0, size);
        int lastPageIndex = first.totalPages() - 1;
        PageResponse<OrderCandidateResponse> middle = orderCandidateService.findOrderCandidatesPage(null, null, null, 1, size);
        PageResponse<OrderCandidateResponse> last = orderCandidateService.findOrderCandidatesPage(null, null, null, lastPageIndex, size);

        assertEquals(size, first.content().size());
        assertEquals(size, middle.content().size());
        assertTrue(last.content().size() >= 1 && last.content().size() <= size);

        Set<String> firstSkus = first.content().stream().map(OrderCandidateResponse::sku).collect(Collectors.toSet());
        Set<String> middleSkus = middle.content().stream().map(OrderCandidateResponse::sku).collect(Collectors.toSet());
        assertTrue(firstSkus.stream().noneMatch(middleSkus::contains), "pages must not overlap");
    }

    @Test
    void pageBeyondTheLastPageReturnsAnEmptyContentNotAnError() {
        PageResponse<OrderCandidateResponse> first = orderCandidateService.findOrderCandidatesPage(null, null, null, 0, 20);
        int wayBeyond = first.totalPages() + 100;

        PageResponse<OrderCandidateResponse> beyond = orderCandidateService.findOrderCandidatesPage(null, null, null, wayBeyond, 20);
        assertTrue(beyond.content().isEmpty());
        assertEquals(first.totalElements(), beyond.totalElements(), "total is unaffected by an out-of-range page");
    }

    @Test
    void filterPlusPaginationNarrowsBothTotalAndContent() {
        PageResponse<OrderCandidateResponse> unfiltered = orderCandidateService.findOrderCandidatesPage(null, null, null, 0, 100);
        PageResponse<OrderCandidateResponse> kitchenOnly = orderCandidateService.findOrderCandidatesPage("BR_KITCHEN", null, null, 0, 100);

        assertTrue(kitchenOnly.totalElements() < unfiltered.totalElements());
        assertTrue(kitchenOnly.content().stream().allMatch(r -> "BR_KITCHEN".equals(r.brandCode())));
    }

    @Test
    void pageSizeIsClampedToMaximum() {
        PageResponse<OrderCandidateResponse> page = orderCandidateService.findOrderCandidatesPage(null, null, null, 0, 10_000);
        assertEquals(OrderCandidateService.MAX_PAGE_SIZE, page.size());
    }

    /** {@link OrderCandidateService#findOrderCandidates} itself must stay
     * exactly as it was - {@code SupplierMasterService} calls it expecting
     * the full, unpaginated result for its own aggregate computation.
     * (Stage 5E Targeted Remediation RC-A, docs/real-data-audit/
     * gops-stage5e-targeted-remediation.md: {@code DashboardService} no
     * longer calls this method at all - it now uses its own dedicated,
     * leaner queries - but this method's own contract is unchanged for
     * every caller that still relies on it.) */
    @Test
    void unpaginatedFindOrderCandidatesStillReturnsEveryRow() {
        List<OrderCandidateResponse> all = orderCandidateService.findOrderCandidates(null, null, null);
        PageResponse<OrderCandidateResponse> paged = orderCandidateService.findOrderCandidatesPage(null, null, null, 0, 100);
        assertEquals(all.size(), (int) paged.totalElements());
    }

    /**
     * Stage 5E Targeted Remediation (RC-E, docs/real-data-audit/
     * gops-stage5e-targeted-remediation.md): the Candidate List response's
     * openPo field must be Open PO alone, never combined with Open Arrival.
     * Uses the same STAGE4-WH-TEST-001 fixture (backend/demo-data/
     * 02-seed.sql, po_qty_1=3, arr_qty_1=999) Stage 4's own
     * LegacyStockReadRepositoryPhysicalQtyIntegrationTest already proved
     * these two are fetched correctly and kept separate at the raw-row
     * level - this proves the SAME thing survives all the way to the
     * response DTO the Frontend actually renders as "発注残".
     */
    @Test
    void responseOpenPoExcludesOpenArrival() {
        PageResponse<OrderCandidateResponse> page =
                orderCandidateService.findOrderCandidatesPage(null, null, "STAGE4-WH-TEST-001", 0, 10);
        assertEquals(1, page.content().size());
        OrderCandidateResponse response = page.content().get(0);
        assertEquals(3, response.openPo(), "openPo must be PO_QTY alone (3), never PO_QTY+ARR_QTY (3+999=1002)");
    }
}
