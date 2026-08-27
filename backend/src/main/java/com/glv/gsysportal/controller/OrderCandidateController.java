package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.service.OrderCandidateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Order Candidate List API (Technical Design 8章, #3).
 * Legacy Read only - no Prototype DB writes occur from this endpoint.
 */
@RestController
public class OrderCandidateController {

    private final OrderCandidateService orderCandidateService;

    public OrderCandidateController(OrderCandidateService orderCandidateService) {
        this.orderCandidateService = orderCandidateService;
    }

    @GetMapping("/api/order-candidates")
    public List<OrderCandidateResponse> getOrderCandidates(
            @RequestParam(required = false) String brandCode,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String keyword
    ) {
        return orderCandidateService.findOrderCandidates(brandCode, supplierCode, keyword);
    }
}
