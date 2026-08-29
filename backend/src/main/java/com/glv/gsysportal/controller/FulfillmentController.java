package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.FulfillmentView;
import com.glv.gsysportal.service.FulfillmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Phase 7-C7A 4章/20章: READ ONLY, open to any authenticated user (matches
 * every other GET endpoint in this codebase - viewing Fulfillment is never a
 * destructive action). */
@RestController
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    public FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping("/api/orders/{id}/fulfillment")
    public FulfillmentView getFulfillment(@PathVariable Long id) {
        return fulfillmentService.getFulfillment(id);
    }
}
