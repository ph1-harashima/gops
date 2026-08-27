package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.AuditEventView;
import com.glv.gsysportal.dto.response.OrderHistoryDetailResponse;
import com.glv.gsysportal.dto.response.OrderHistorySummaryResponse;
import com.glv.gsysportal.service.OrderHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Order History (implementation instructions 24章/25章/26章). READ ONLY. */
@RestController
public class OrderHistoryController {

    private final OrderHistoryService orderHistoryService;

    public OrderHistoryController(OrderHistoryService orderHistoryService) {
        this.orderHistoryService = orderHistoryService;
    }

    @GetMapping("/api/orders/history")
    public List<OrderHistorySummaryResponse> history(
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String brandCode,
            @RequestParam(required = false) String status) {
        return orderHistoryService.list(supplierCode, brandCode, status);
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
