package com.glv.gsysportal.controller;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.OrderStatusChangeResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.OrderStatusTransitionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * READY_TO_ORDER -> DRAFT (implementation instructions 13章). Deliberately
 * under {@code /api/orders/{id}/...}, not {@code /api/orders/drafts/{id}/...}
 * - matches the literal endpoint given in implementation instructions 13章.
 * Both are covered by the same {@code anyRequest().authenticated()} rule in
 * SecurityConfig (Step 2), so no Security configuration change was needed
 * for this Step (implementation instructions 18章).
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

    @PostMapping("/api/orders/{id}/return-to-draft")
    public OrderStatusChangeResponse returnToDraft(@PathVariable Long id) {
        PortalOrder order = statusTransitionService.returnToDraft(id, currentUserProvider.currentUsername());
        return new OrderStatusChangeResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getStatus(), order.getUpdatedBy(), order.getUpdatedAt()
        );
    }
}
