package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.BrandSummaryResponse;
import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.service.OrderCandidateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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

    /**
     * Stage 5E Targeted Remediation (RC-B, docs/real-data-audit/
     * gops-stage5e-targeted-remediation.md): lightweight Brand code->name
     * lookup - {@code CandidateListPage}'s Filter Chip resolves the
     * selected Brand's display name from this, instead of the full
     * {@code GET /api/dashboard} response (Stage 5D's confirmed root cause
     * of the background freeze every Candidate List visit triggered).
     */
    @GetMapping("/api/brands")
    public List<BrandSummaryResponse> getBrands() {
        Map<String, String> brandNames = orderCandidateService.findBrandNames();
        return brandNames.entrySet().stream()
                .map(entry -> new BrandSummaryResponse(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> a.brandCode().compareTo(b.brandCode()))
                .toList();
    }
}
