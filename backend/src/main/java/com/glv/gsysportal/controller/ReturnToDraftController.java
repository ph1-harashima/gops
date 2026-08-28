package com.glv.gsysportal.controller;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.OrderStatusChangeResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.OrderStatusTransitionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APPROVED -> DRAFT (implementation instructions 13章; Phase 7-C1: the source
 * Status became APPROVED, and the action became ADMIN-only - un-approving an
 * approved Order invalidates an ADMIN decision, so only an ADMIN may do it).
 */
@RestController
public class ReturnToDraftController {

    private final OrderStatusTransitionService statusTransitionService;
    private final CurrentUserProvider currentUserProvider;

    public ReturnToDraftController(OrderStatusTransitionService statusTransitionService,
                                    CurrentUserProvider currentUserProvider) {
        this.statusTransitionService = statusTransitionService;
        this.currentUserProvider = currentUserProvider;
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/return-to-draft")
    public OrderStatusChangeResponse returnToDraft(@PathVariable Long id) {
        PortalOrder order = statusTransitionService.returnToDraft(id, currentUserProvider.currentUsername());
        return new OrderStatusChangeResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getStatus(), order.getUpdatedBy(), order.getUpdatedAt()
        );
    }
}
