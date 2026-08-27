package com.glv.gsysportal.controller;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.UpdateDraftRequest;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.dto.response.OrderStatusChangeResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.OrderDraftService;
import com.glv.gsysportal.service.OrderStatusTransitionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Requires authentication (SecurityConfig: anyRequest().authenticated() for
 * /api/orders/**) so performed_by/created_by is a real Portal user, never a
 * fixed string (implementation instructions 15章).
 */
@RestController
public class OrderDraftController {

    private final OrderDraftService orderDraftService;
    private final OrderStatusTransitionService statusTransitionService;
    private final CurrentUserProvider currentUserProvider;

    public OrderDraftController(OrderDraftService orderDraftService,
                                 OrderStatusTransitionService statusTransitionService,
                                 CurrentUserProvider currentUserProvider) {
        this.orderDraftService = orderDraftService;
        this.statusTransitionService = statusTransitionService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/orders/drafts")
    public ResponseEntity<OrderDraftResponse> createDraft(@Valid @RequestBody CreateDraftRequest request) {
        OrderDraftResponse response = orderDraftService.createDraft(request, currentUserProvider.currentUsername());
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/api/orders/drafts/{id}")
    public OrderDraftResponse getDraft(@PathVariable Long id) {
        return orderDraftService.getDraft(id);
    }

    @PutMapping("/api/orders/drafts/{id}")
    public OrderDraftResponse updateDraft(@PathVariable Long id, @RequestBody UpdateDraftRequest request) {
        return orderDraftService.updateDraft(id, request, currentUserProvider.currentUsername());
    }

    /** DRAFT -> READY_TO_ORDER (implementation instructions 7章). */
    @PostMapping("/api/orders/drafts/{id}/confirm")
    public OrderStatusChangeResponse confirm(@PathVariable Long id) {
        PortalOrder order = statusTransitionService.confirm(id, currentUserProvider.currentUsername());
        return toStatusChangeResponse(order);
    }

    private static OrderStatusChangeResponse toStatusChangeResponse(PortalOrder order) {
        return new OrderStatusChangeResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getStatus(), order.getUpdatedBy(), order.getUpdatedAt()
        );
    }
}
