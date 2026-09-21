package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.PriceChangeCandidateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 4 Targeted Real-Data Remediation (Remediation D, docs/real-data-audit/
 * gops-stage4-targeted-real-data-remediation.md) -
 * GET /api/price-changes/legacy-items previously returned every matching
 * row unpaginated - same root cause and fix shape as
 * OrderCandidateServicePaginationIntegrationTest.
 */
@SpringBootTest
@ActiveProfiles("test")
class PriceChangeSetServiceCandidatePaginationIntegrationTest {

    @Autowired
    private PriceChangeSetService priceChangeSetService;

    @Test
    void defaultPagingReturnsFirstPage() {
        PageResponse<PriceChangeCandidateResponse> page = priceChangeSetService.searchCandidatesPage(null, null, null, null, null);
        assertEquals(0, page.page());
        assertEquals(PriceChangeSetService.DEFAULT_PAGE_SIZE, page.size());
        assertTrue(page.content().size() <= page.size());
        assertTrue(page.totalElements() >= 19, "backend/demo-data/02-seed.sql seeds at least 19 ms_item rows");
    }

    @Test
    void firstAndLastPageCoverDistinctRowsWithNoOverlap() {
        int size = 5;
        PageResponse<PriceChangeCandidateResponse> first = priceChangeSetService.searchCandidatesPage(null, null, null, 0, size);
        PageResponse<PriceChangeCandidateResponse> second = priceChangeSetService.searchCandidatesPage(null, null, null, 1, size);

        assertEquals(size, first.content().size());
        assertEquals(size, second.content().size());
        Set<String> firstSkus = first.content().stream().map(PriceChangeCandidateResponse::itemCd).collect(Collectors.toSet());
        Set<String> secondSkus = second.content().stream().map(PriceChangeCandidateResponse::itemCd).collect(Collectors.toSet());
        assertTrue(firstSkus.stream().noneMatch(secondSkus::contains), "pages must not overlap");
    }

    @Test
    void pageBeyondTheLastPageReturnsAnEmptyContentNotAnError() {
        PageResponse<PriceChangeCandidateResponse> first = priceChangeSetService.searchCandidatesPage(null, null, null, 0, 20);
        int wayBeyond = first.totalPages() + 100;

        PageResponse<PriceChangeCandidateResponse> beyond = priceChangeSetService.searchCandidatesPage(null, null, null, wayBeyond, 20);
        assertTrue(beyond.content().isEmpty());
        assertEquals(first.totalElements(), beyond.totalElements());
    }

    @Test
    void filterPlusPaginationNarrowsBothTotalAndContent() {
        PageResponse<PriceChangeCandidateResponse> unfiltered = priceChangeSetService.searchCandidatesPage(null, null, null, 0, 100);
        PageResponse<PriceChangeCandidateResponse> kitchenOnly = priceChangeSetService.searchCandidatesPage("BR_KITCHEN", null, null, 0, 100);

        assertTrue(kitchenOnly.totalElements() < unfiltered.totalElements());
        assertTrue(kitchenOnly.content().stream().allMatch(r -> "BR_KITCHEN".equals(r.brandCode())));
    }

    @Test
    void pageSizeIsClampedToMaximum() {
        PageResponse<PriceChangeCandidateResponse> page = priceChangeSetService.searchCandidatesPage(null, null, null, 0, 10_000);
        assertEquals(PriceChangeSetService.MAX_PAGE_SIZE, page.size());
    }

    /** {@link PriceChangeSetService#searchCandidates} itself must stay
     * unpaginated - left unchanged for any other/future caller expecting
     * the full result. */
    @Test
    void unpaginatedSearchCandidatesStillReturnsEveryRow() {
        int all = priceChangeSetService.searchCandidates(null, null, null).size();
        PageResponse<PriceChangeCandidateResponse> paged = priceChangeSetService.searchCandidatesPage(null, null, null, 0, 100);
        assertEquals(all, (int) paged.totalElements());
    }
}
