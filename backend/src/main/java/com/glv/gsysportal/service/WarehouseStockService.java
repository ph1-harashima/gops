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

/**
 * Warehouse Stock Visibility Foundation (Phase 8-G 8章/9章/10章). Pure
 * Legacy READ ONLY - same guarantees as {@link ArrivalService}. Deliberately
 * has no dependency on {@link com.glv.gsysportal.repository.legacy.ArrivalReadRepository}
 * or {@link com.glv.gsysportal.repository.legacy.FulfillmentReadRepository}
 * (Phase 8-G 11章 - Arrival and Warehouse Stock are never composed together
 * anywhere in this codebase, not even at the Service layer).
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

        long total = warehouseStockReadRepository.countList(filter);
        List<WarehouseStockSummaryResponse> content = warehouseStockReadRepository.findList(filter, clampedSize, offset).stream()
                .map(WarehouseStockService::toSummary)
                .toList();
        return PageResponse.of(content, clampedPage, clampedSize, total);
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
