package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.AuditEventView;
import com.glv.gsysportal.dto.response.OrderHistoryDetailResponse;
import com.glv.gsysportal.dto.response.OrderHistorySummaryResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.service.OrderHistoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Order History (implementation instructions 24章/25章/26章). READ ONLY. */
@RestController
public class OrderHistoryController {

    private final OrderHistoryService orderHistoryService;

    public OrderHistoryController(OrderHistoryService orderHistoryService) {
        this.orderHistoryService = orderHistoryService;
    }

    /** Phase 8-J 3章/4章: page/size/hasAttention added - Backend-paginated
     * and Backend-filtered, matching the PageResponse pattern already used
     * by /api/arrivals, /api/warehouse-stock, /api/stock-sales (Phase
     * 8-G/8-H). hasAttention replaces the previous Frontend-only display
     * filter of the same name in OrderHistoryListPage.tsx. */
    @GetMapping("/api/orders/history")
    public PageResponse<OrderHistorySummaryResponse> history(
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String brandCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNoKeyword,
            @RequestParam(required = false) String itemKeyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate updatedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate updatedTo,
            @RequestParam(required = false) Boolean hasAttention,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return orderHistoryService.list(supplierCode, brandCode, status, orderNoKeyword, itemKeyword,
                updatedFrom, updatedTo, hasAttention, page, size);
    }

    @GetMapping("/api/orders/{id}")
    public OrderHistoryDetailResponse detail(@PathVariable Long id) {
        return orderHistoryService.detail(id);
    }

    @GetMapping("/api/orders/{id}/events")
    public List<AuditEventView> events(@PathVariable Long id) {
        return orderHistoryService.events(id);
    }
}
