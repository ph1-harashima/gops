package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.FollowUpCase;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.CreateReorderDraftRequest;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * "再発注Draftを作成" (Phase 7-C7A 15章/16章). Deliberately built as a THIN
 * wrapper around the EXISTING {@link OrderDraftService#createDraft} - zero
 * change to Order creation itself (7-C7A 28章's STOP condition on "既存Order
 * Workflowの大幅変更" was evaluated and does not apply: this class only
 * calls the existing method, then stamps 3 nullable Reference columns onto
 * the row it created). Quantity is NEVER auto-decided from Outstanding Qty -
 * the created Draft's Order Qty is whatever {@code createDraft} normally
 * computes (Legacy Recommended Qty), exactly like any other fresh Draft.
 */
@Service
public class ReorderService {

    private final OrderDraftService orderDraftService;
    private final PortalOrderRepository portalOrderRepository;
    private final FollowUpCaseService followUpCaseService;
    private final AuditEventRepository auditEventRepository;

    public ReorderService(OrderDraftService orderDraftService,
                           PortalOrderRepository portalOrderRepository,
                           FollowUpCaseService followUpCaseService,
                           AuditEventRepository auditEventRepository) {
        this.orderDraftService = orderDraftService;
        this.portalOrderRepository = portalOrderRepository;
        this.followUpCaseService = followUpCaseService;
        this.auditEventRepository = auditEventRepository;
    }

    /** ADMIN only (enforced at the Controller, 7-C7A 20章). */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public OrderDraftResponse createReorderDraft(Long followUpCaseId, CreateReorderDraftRequest request, String performedBy) {
        FollowUpCase followUpCase = followUpCaseService.requireCase(followUpCaseId);

        // Step A (Legacy READ) + Step B (Prototype write) - identical to any
        // other Create Draft call, see OrderDraftService's own Javadoc.
        OrderDraftResponse created = orderDraftService.createDraft(
                new CreateDraftRequest(request.skus(), null, null, null), performedBy);

        PortalOrder order = portalOrderRepository.findById(created.id())
                .orElseThrow(() -> new IllegalStateException("Just-created Order " + created.id() + " not found"));
        order.setSourceOrderId(followUpCase.getPortalOrderId());
        order.setSourceFollowUpCaseId(followUpCaseId);
        order.setReorderReason(request.reorderReason());
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(OffsetDateTime.now());
        portalOrderRepository.save(order);

        auditEventRepository.save(new AuditEvent(order.getId(), null, AuditEvent.REORDER_CREATED,
                "sourceOrderId", null, String.valueOf(followUpCase.getPortalOrderId()), performedBy, OffsetDateTime.now()));

        return OrderDraftService.toResponse(order, null);
    }
}
