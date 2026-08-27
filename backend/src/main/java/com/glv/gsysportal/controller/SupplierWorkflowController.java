package com.glv.gsysportal.controller;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.response.OrderStatusChangeResponse;
import com.glv.gsysportal.dto.response.SupplierResponseView;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.OrderStatusTransitionService;
import com.glv.gsysportal.service.SupplierResponseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo Send (implementation instructions 3章) and Supplier Response
 * GET/PUT/Confirm (9章/10章/20章), all under {@code /api/orders/{id}/...}
 * (matching implementation instructions' literal endpoint paths - none are
 * under {@code /api/orders/drafts/**}, so this is a separate controller from
 * {@link OrderDraftController}).
 */
@RestController
public class SupplierWorkflowController {

    private final OrderStatusTransitionService statusTransitionService;
    private final SupplierResponseService supplierResponseService;
    private final CurrentUserProvider currentUserProvider;

    public SupplierWorkflowController(OrderStatusTransitionService statusTransitionService,
                                       SupplierResponseService supplierResponseService,
                                       CurrentUserProvider currentUserProvider) {
        this.statusTransitionService = statusTransitionService;
        this.supplierResponseService = supplierResponseService;
        this.currentUserProvider = currentUserProvider;
    }

    /** READY_TO_ORDER -> SENT -> AWAITING_SUPPLIER. No real email is sent. */
    @PostMapping("/api/orders/{id}/demo-send")
    public OrderStatusChangeResponse demoSend(@PathVariable Long id) {
        PortalOrder order = statusTransitionService.demoSend(id, currentUserProvider.currentUsername());
        return toStatusChangeResponse(order);
    }

    @GetMapping("/api/orders/{id}/supplier-response")
    public SupplierResponseView getSupplierResponse(@PathVariable Long id) {
        return supplierResponseService.getSupplierResponse(id);
    }

    @PutMapping("/api/orders/{id}/supplier-response")
    public SupplierResponseView saveSupplierResponse(@PathVariable Long id, @RequestBody SaveSupplierResponseRequest request) {
        return supplierResponseService.saveSupplierResponse(id, request, currentUserProvider.currentUsername());
    }

    /** AWAITING_SUPPLIER -> SUPPLIER_CONFIRMED. */
    @PostMapping("/api/orders/{id}/supplier-response/confirm")
    public OrderStatusChangeResponse confirmSupplierResponse(@PathVariable Long id) {
        PortalOrder order = supplierResponseService.confirmSupplierResponse(id, currentUserProvider.currentUsername());
        return toStatusChangeResponse(order);
    }

    private static OrderStatusChangeResponse toStatusChangeResponse(PortalOrder order) {
        return new OrderStatusChangeResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getStatus(), order.getUpdatedBy(), order.getUpdatedAt()
        );
    }
}
