package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.StockSalesSummaryResponse;
import com.glv.gsysportal.service.StockSalesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Stock/Sales Visibility Foundation (Phase 8-H 15章). READ ONLY, open to
 * any authenticated user - same access pattern as every other GET endpoint
 * in this codebase. */
@RestController
public class StockSalesController {

    private final StockSalesService stockSalesService;

    public StockSalesController(StockSalesService stockSalesService) {
        this.stockSalesService = stockSalesService;
    }

    @GetMapping("/api/stock-sales")
    public PageResponse<StockSalesSummaryResponse> list(
            @RequestParam(required = false) String skuKeyword,
            @RequestParam(required = false) String brandCode,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) Integer minStock,
            @RequestParam(required = false) Integer maxStock,
            @RequestParam(required = false) Integer minSales,
            @RequestParam(required = false) Integer maxSales,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return stockSalesService.list(skuKeyword, brandCode, supplierCode, minStock, maxStock, minSales, maxSales, page, size);
    }

    @GetMapping("/api/stock-sales/{sku}")
    public StockSalesSummaryResponse detail(@PathVariable String sku) {
        return stockSalesService.detail(sku);
    }
}
