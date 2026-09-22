package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.WarehouseStockDetailResponse;
import com.glv.gsysportal.dto.response.WarehouseStockSummaryResponse;
import com.glv.gsysportal.exception.SkuNotFoundException;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.WarehouseStockReadRepository;
import com.glv.gsysportal.repository.legacy.WarehouseStockReadRepository.WarehouseStockListFilter;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import com.glv.gsysportal.repository.legacy.row.LegacyWarehouseStockRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Warehouse Stock Visibility Foundation (Phase 8-G 8章/9章/10章). Pure
 * Legacy READ ONLY - same guarantees as {@link ArrivalService}. Deliberately
 * has no dependency on {@link com.glv.gsysportal.repository.legacy.ArrivalReadRepository}
 * or {@link com.glv.gsysportal.repository.legacy.FulfillmentReadRepository}
 * (Phase 8-G 11章 - Arrival and Warehouse Stock are never composed together
 * anywhere in this codebase, not even at the Service layer).
 *
 * <p>Warehouse Stock Production-Scale Remediation (docs/real-data-audit/
 * gops-warehouse-stock-production-scale-remediation.md §10): the
 * unfiltered {@code countList}/{@code findList} calls each independently
 * cost several seconds at Production scale (measured on the isolated
 * Snapshot - neither shows the old pathological filesort, but a
 * ~567,000-row {@code ms_item x ms_stk} nested-loop join has a real,
 * measured floor of several seconds either way, with no Legacy index
 * change available this Stage). They are entirely independent reads with
 * no data dependency on each other, so this method runs them
 * concurrently (one dedicated, short-lived worker thread - the same
 * per-call-executor pattern {@code DashboardRefreshService} already
 * established, not a new persistent thread pool) instead of serially -
 * roughly halving the unfiltered-case wall-clock cost. This does not, by
 * itself, bring the unfiltered case under this Stage's <=2s target (see
 * the remediation doc's own honest reporting) - it is a real, measured
 * improvement layered on top of the Phase 1/Phase 2 query redesign, nothing
 * more.
 */
@Service
public class WarehouseStockService {

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    private final WarehouseStockReadRepository warehouseStockReadRepository;
    private final LegacyStockReadRepository legacyStockReadRepository;

    public WarehouseStockService(WarehouseStockReadRepository warehouseStockReadRepository,
                                  LegacyStockReadRepository legacyStockReadRepository) {
        this.warehouseStockReadRepository = warehouseStockReadRepository;
        this.legacyStockReadRepository = legacyStockReadRepository;
    }

    public PageResponse<WarehouseStockSummaryResponse> list(String skuKeyword, String brandCode, String warehouseCode,
                                                              Integer minQty, Integer maxQty, Integer page, Integer size) {
        WarehouseStockListFilter filter = new WarehouseStockListFilter(
                blankToNull(skuKeyword), blankToNull(brandCode), blankToNull(warehouseCode), minQty, maxQty);
        int clampedSize = clampSize(size);
        int clampedPage = page == null || page < 0 ? 0 : page;
        int offset = clampedPage * clampedSize;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Long> totalFuture = CompletableFuture.supplyAsync(
                    () -> warehouseStockReadRepository.countList(filter), executor);
            List<WarehouseStockSummaryResponse> content = warehouseStockReadRepository.findList(filter, clampedSize, offset).stream()
                    .map(WarehouseStockService::toSummary)
                    .toList();
            long total = totalFuture.join();
            return PageResponse.of(content, clampedPage, clampedSize, total);
        } finally {
            executor.shutdown();
        }
    }

    /** Warehouse Stock Detail (Phase 8-G 10章) - all physical-warehouse rows
     * for one SKU, plus that SKU's Item/Brand identity (looked up the same
     * way SkuDetailService does, via {@link LegacyStockReadRepository}, so a
     * SKU that exists in Legacy but currently has zero ms_stk rows still
     * resolves - only a genuinely unknown SKU throws). */
    public WarehouseStockDetailResponse detail(String sku) {
        List<LegacyStockRow> stockRows = legacyStockReadRepository.findBySkus(Set.of(sku));
        if (stockRows.isEmpty()) {
            throw new SkuNotFoundException(Set.of(sku));
        }
        LegacyStockRow item = stockRows.get(0);

        List<WarehouseStockSummaryResponse> warehouses = warehouseStockReadRepository.findByItemCd(sku).stream()
                .map(WarehouseStockService::toSummary)
                .toList();

        return new WarehouseStockDetailResponse(item.itemCd(), item.itemName(), item.brandCd(), item.brandName(), warehouses);
    }

    private static WarehouseStockSummaryResponse toSummary(LegacyWarehouseStockRow row) {
        return new WarehouseStockSummaryResponse(
                row.whCd(), row.itemCd(), row.itemName(), row.brandCd(), row.brandName(),
                row.stkQty(), row.updateDatetime());
    }

    private static int clampSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
