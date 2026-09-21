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
     * exactly as it was - DashboardService/SupplierMasterService both call
     * it expecting the full, unpaginated result for their own aggregate/KPI
     * computation. */
    @Test
    void unpaginatedFindOrderCandidatesStillReturnsEveryRow() {
        List<OrderCandidateResponse> all = orderCandidateService.findOrderCandidates(null, null, null);
        PageResponse<OrderCandidateResponse> paged = orderCandidateService.findOrderCandidatesPage(null, null, null, 0, 100);
        assertEquals(all.size(), (int) paged.totalElements());
    }
}
