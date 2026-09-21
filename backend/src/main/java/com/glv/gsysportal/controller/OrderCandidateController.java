package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.service.OrderCandidateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order Candidate List API (Technical Design 8章, #3).
 * Legacy Read only - no Prototype DB writes occur from this endpoint.
 *
 * <p>Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
 * gops-stage4-targeted-real-data-remediation.md): Backend-paginated, same
 * page/size contract as {@code StockSalesController} - a confirmed real
 * Brand has 18,596 SKUs, and this endpoint previously returned every
 * matching row in one response.
 */
@RestController
public class OrderCandidateController {

    private final OrderCandidateService orderCandidateService;

    public OrderCandidateController(OrderCandidateService orderCandidateService) {
        this.orderCandidateService = orderCandidateService;
    }

    @GetMapping("/api/order-candidates")
    public PageResponse<OrderCandidateResponse> getOrderCandidates(
            @RequestParam(required = false) String brandCode,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return orderCandidateService.findOrderCandidatesPage(brandCode, supplierCode, keyword, page, size);
    }
}
