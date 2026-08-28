package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.UpdateDraftRequest;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.OrderDraftService;
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
 *
 * Phase 7-C1: the Step 3 {@code /confirm} endpoint (DRAFT -> READY_TO_ORDER)
 * was replaced by the approval Workflow - see {@link OrderApprovalController}.
 */
@RestController
public class OrderDraftController {

    private final OrderDraftService orderDraftService;
    private final CurrentUserProvider currentUserProvider;

    public OrderDraftController(OrderDraftService orderDraftService,
                                 CurrentUserProvider currentUserProvider) {
        this.orderDraftService = orderDraftService;
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
        return orderDraftService.updateDraft(id, request,
                currentUserProvider.currentUsername(), currentUserProvider.isAdmin());
    }

}
