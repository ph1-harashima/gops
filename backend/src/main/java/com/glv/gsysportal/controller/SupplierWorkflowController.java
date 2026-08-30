package com.glv.gsysportal.controller;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.AgreeResponseRequest;
import com.glv.gsysportal.dto.request.CreateRevisionRequest;
import com.glv.gsysportal.dto.request.ReopenAgreementRequest;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.response.OrderRevisionSummary;
import com.glv.gsysportal.dto.response.OrderStatusChangeResponse;
import com.glv.gsysportal.dto.response.SupplierResponseHistoryEntry;
import com.glv.gsysportal.dto.response.SupplierResponseView;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.OrderRevisionService;
import com.glv.gsysportal.service.OrderStatusTransitionService;
import com.glv.gsysportal.service.SupplierResponseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    private final OrderRevisionService orderRevisionService;
    private final CurrentUserProvider currentUserProvider;

    public SupplierWorkflowController(OrderStatusTransitionService statusTransitionService,
                                       SupplierResponseService supplierResponseService,
                                       OrderRevisionService orderRevisionService,
                                       CurrentUserProvider currentUserProvider) {
        this.statusTransitionService = statusTransitionService;
        this.supplierResponseService = supplierResponseService;
        this.orderRevisionService = orderRevisionService;
        this.currentUserProvider = currentUserProvider;
    }

    /** READY_TO_ORDER -> SENT -> AWAITING_SUPPLIER. No real email is sent. */
    @PostMapping("/api/orders/{id}/demo-send")
    public OrderStatusChangeResponse demoSend(@PathVariable Long id) {
        PortalOrder order = statusTransitionService.demoSend(id, currentUserProvider.currentUsername());
        return toStatusChangeResponse(order);
    }

    /** Phase 7-H (EDI発注Workflow Foundation): same APPROVED -> SENT ->
     * AWAITING_SUPPLIER transition as demoSend, for a Supplier ordered from
     * over their own EDI system rather than Email - see
     * OrderStatusTransitionService.recordEdiSend's Javadoc. No real EDI
     * file/connection - a record only. Same Permission as demo-send (no
     * @PreAuthorize - any authenticated user). */
    @PostMapping("/api/orders/{id}/edi-send")
    public OrderStatusChangeResponse ediSend(@PathVariable Long id) {
        PortalOrder order = statusTransitionService.recordEdiSend(id, currentUserProvider.currentUsername());
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

    // --- Phase 7-C5: Supplier Response Revision / Agreement Workflow ---

    /** Order Detail's browsable Revision History (Rev1, Rev2, ...) - any
     * authenticated user, same visibility as every other read endpoint. */
    @GetMapping("/api/orders/{id}/revisions")
    public List<OrderRevisionSummary> getRevisions(@PathVariable Long id) {
        return orderRevisionService.getRevisionHistory(id);
    }

    /** "修正版を作成" (7-C5 13章): SUPPLIER_CONFIRMED -> DRAFT. ADMIN only -
     * mirrors {@link OrderApprovalController}'s existing convention that
     * every Workflow-moving Business Action beyond plain Draft editing is
     * ADMIN-gated (7-C5 15章 explicitly reuses, rather than redesigns, the
     * existing single-stage Approval Workflow for the resulting Draft). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/revisions")
    public OrderStatusChangeResponse createRevision(@PathVariable Long id, @RequestBody CreateRevisionRequest request) {
        PortalOrder order = orderRevisionService.createCorrection(id, request, currentUserProvider.currentUsername());
        return toStatusChangeResponse(order);
    }

    /** Response History (Response1, Response2, ...). */
    @GetMapping("/api/orders/{id}/responses")
    public List<SupplierResponseHistoryEntry> getResponses(@PathVariable Long id) {
        return supplierResponseService.getResponseHistory(id);
    }

    /** Past, READ ONLY Response for a specific Revision (7-C5 21章). */
    @GetMapping("/api/orders/{id}/responses/by-revision/{revisionNo}")
    public SupplierResponseView getResponseByRevision(@PathVariable Long id, @PathVariable int revisionNo) {
        return supplierResponseService.getSupplierResponseHistory(id, revisionNo);
    }

    /** SUPPLIER_CONFIRMED -> AGREED (7-C5 11章). ADMIN only. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/responses/{responseId}/agree")
    public OrderStatusChangeResponse agree(@PathVariable Long id, @PathVariable Long responseId,
                                            @RequestBody(required = false) AgreeResponseRequest request) {
        AgreeResponseRequest body = request != null ? request : new AgreeResponseRequest(false);
        PortalOrder order = supplierResponseService.agree(id, responseId, body, currentUserProvider.currentUsername());
        return toStatusChangeResponse(order);
    }

    /** AGREED -> SUPPLIER_CONFIRMED with a mandatory reason (7-C5 18章). ADMIN only. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/responses/{responseId}/reopen")
    public OrderStatusChangeResponse reopen(@PathVariable Long id, @PathVariable Long responseId,
                                             @RequestBody ReopenAgreementRequest request) {
        PortalOrder order = supplierResponseService.reopenAgreement(id, responseId, request, currentUserProvider.currentUsername());
        return toStatusChangeResponse(order);
    }

    private static OrderStatusChangeResponse toStatusChangeResponse(PortalOrder order) {
        return new OrderStatusChangeResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getStatus(), order.getUpdatedBy(), order.getUpdatedAt()
        );
    }
}
