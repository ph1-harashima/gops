package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.SkuExpectedRestockRequest;
import com.glv.gsysportal.dto.response.SkuDetailResponse;
import com.glv.gsysportal.dto.response.SkuManufacturerStockoutHistoryEntryResponse;
import com.glv.gsysportal.dto.response.SkuRestockExpectationResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.SkuDetailService;
import com.glv.gsysportal.service.SkuRestockExpectationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SKU Detail (implementation instructions Step 5 4章). READ ONLY, except
 * the Post-Freeze Business Refinement restock-expectation endpoints below
 * (Portal-only, operational data - same "no ADMIN gate" permission level as
 * Follow-up Case creation, per re-audit doc §9's explicit instruction to
 * reuse an existing operational-edit permission rather than add a new Role). */
@RestController
public class SkuDetailController {

    private final SkuDetailService skuDetailService;
    private final SkuRestockExpectationService restockExpectationService;
    private final CurrentUserProvider currentUserProvider;

    public SkuDetailController(SkuDetailService skuDetailService,
                                SkuRestockExpectationService restockExpectationService,
                                CurrentUserProvider currentUserProvider) {
        this.skuDetailService = skuDetailService;
        this.restockExpectationService = restockExpectationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/items/{sku}/ordering-context")
    public SkuDetailResponse orderingContext(@PathVariable String sku) {
        return skuDetailService.getDetail(sku);
    }

    @GetMapping("/api/items/{sku}/restock-expectation")
    public SkuRestockExpectationResponse restockExpectation(@PathVariable String sku) {
        return restockExpectationService.get(sku);
    }

    @PutMapping("/api/items/{sku}/restock-expectation")
    public SkuRestockExpectationResponse updateRestockExpectation(@PathVariable String sku,
                                                                    @Valid @RequestBody SkuExpectedRestockRequest request) {
        return restockExpectationService.update(sku, request, currentUserProvider.currentUsername());
    }

    /** Post-Freeze Business Refinement 2 (requirements doc §11/§21) -
     * Business-facing Manufacturer Stockout History, oldest first. */
    @GetMapping("/api/items/{sku}/restock-expectation/history")
    public List<SkuManufacturerStockoutHistoryEntryResponse> restockExpectationHistory(@PathVariable String sku) {
        return restockExpectationService.getHistory(sku);
    }
}
