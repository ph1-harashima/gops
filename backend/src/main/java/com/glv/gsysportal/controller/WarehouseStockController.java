package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.WarehouseStockDetailResponse;
import com.glv.gsysportal.dto.response.WarehouseStockSummaryResponse;
import com.glv.gsysportal.service.WarehouseStockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Warehouse Stock Visibility Foundation (Phase 8-G 15章). READ ONLY, open
 * to any authenticated user. */
@RestController
public class WarehouseStockController {

    private final WarehouseStockService warehouseStockService;

    public WarehouseStockController(WarehouseStockService warehouseStockService) {
        this.warehouseStockService = warehouseStockService;
    }

    @GetMapping("/api/warehouse-stock")
    public PageResponse<WarehouseStockSummaryResponse> list(
            @RequestParam(required = false) String skuKeyword,
            @RequestParam(required = false) String brandCode,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) Integer minQty,
            @RequestParam(required = false) Integer maxQty,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return warehouseStockService.list(skuKeyword, brandCode, warehouseCode, minQty, maxQty, page, size);
    }

    @GetMapping("/api/warehouse-stock/{sku}")
    public WarehouseStockDetailResponse detail(@PathVariable String sku) {
        return warehouseStockService.detail(sku);
    }
}
