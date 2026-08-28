package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.SkuDetailResponse;
import com.glv.gsysportal.service.SkuDetailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** SKU Detail (implementation instructions Step 5 4章). READ ONLY. */
@RestController
public class SkuDetailController {

    private final SkuDetailService skuDetailService;

    public SkuDetailController(SkuDetailService skuDetailService) {
        this.skuDetailService = skuDetailService;
    }

    @GetMapping("/api/items/{sku}/ordering-context")
    public SkuDetailResponse orderingContext(@PathVariable String sku) {
        return skuDetailService.getDetail(sku);
    }
}
