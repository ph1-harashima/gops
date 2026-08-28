package com.glv.gsysportal.controller;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.ReturnForCorrectionRequest;
import com.glv.gsysportal.dto.response.OrderStatusChangeResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.OrderStatusTransitionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 7-C1: explicit Business Action endpoints for the approval Workflow
 * (Target Design 6章) - never a generic Status update API. ADMIN-only
 * actions are enforced HERE with @PreAuthorize (Backend enforcement, 7-C1
 * 4章/17章) - Frontend button visibility is a courtesy, not the control.
 * An OPERATOR calling approve/return directly gets 403 FORBIDDEN.
 */
@RestController
public class OrderApprovalController {

    private final OrderStatusTransitionService statusTransitionService;
    private final CurrentUserProvider currentUserProvider;

    public OrderApprovalController(OrderStatusTransitionService statusTransitionService,
                                    CurrentUserProvider currentUserProvider) {
        this.statusTransitionService = statusTransitionService;
        this.currentUserProvider = currentUserProvider;
    }

    /** DRAFT -> PENDING_APPROVAL. Creator or ADMIN (checked in the service). */
    @PostMapping("/api/orders/{id}/submit-for-approval")
    public OrderStatusChangeResponse submitForApproval(@PathVariable Long id) {
        PortalOrder order = statusTransitionService.submitForApproval(
                id, currentUserProvider.currentUsername(), currentUserProvider.isAdmin());
        return toResponse(order);
    }

    /** PENDING_APPROVAL -> APPROVED. ADMIN only. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/approve")
    public OrderStatusChangeResponse approve(@PathVariable Long id) {
        PortalOrder order = statusTransitionService.approve(id, currentUserProvider.currentUsername());
        return toResponse(order);
    }

    /** PENDING_APPROVAL -> DRAFT with a mandatory reason. ADMIN only. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/return-for-correction")
    public OrderStatusChangeResponse returnForCorrection(@PathVariable Long id,
                                                          @RequestBody ReturnForCorrectionRequest request) {
        PortalOrder order = statusTransitionService.returnForCorrection(
                id, request.reason(), currentUserProvider.currentUsername());
        return toResponse(order);
    }

    private static OrderStatusChangeResponse toResponse(PortalOrder order) {
        return new OrderStatusChangeResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getStatus(), order.getUpdatedBy(), order.getUpdatedAt()
        );
    }
}
