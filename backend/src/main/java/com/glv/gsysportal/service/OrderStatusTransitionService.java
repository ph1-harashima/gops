package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.domain.SupplierResponse;
import com.glv.gsysportal.domain.SupplierResponseDetail;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.InvalidStatusTransitionException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.SupplierResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every Workflow Status transition of the Prototype (Phase 7-C1 reshaped the
 * Step 3 Confirm into the approval Workflow of Target Design 5.1/6章):
 * submit-for-approval, approve (plain or with-changes), return-for-correction,
 * return-to-draft, demo send. Each runs entirely inside one Prototype WRITE
 * transaction: fetch -> guard the transition -> re-validate -> mutate ->
 * save -> write Audit, so a Status change with no matching Audit row can
 * never occur.
 */
@Service
public class OrderStatusTransitionService {

    private final PortalOrderRepository portalOrderRepository;
    private final AuditEventRepository auditEventRepository;
    private final SupplierResponseRepository supplierResponseRepository;
    private final PoPreviewValidator validator;
    private final PrototypePoNoGenerator poNoGenerator;

    public OrderStatusTransitionService(PortalOrderRepository portalOrderRepository,
                                         AuditEventRepository auditEventRepository,
                                         SupplierResponseRepository supplierResponseRepository,
                                         PoPreviewValidator validator,
                                         PrototypePoNoGenerator poNoGenerator) {
        this.portalOrderRepository = portalOrderRepository;
        this.auditEventRepository = auditEventRepository;
        this.supplierResponseRepository = supplierResponseRepository;
        this.validator = validator;
        this.poNoGenerator = poNoGenerator;
    }

    /**
     * DRAFT -> PENDING_APPROVAL (Phase 7-C1 8章). Permitted for the Draft's
     * creator or any ADMIN (minimal ownership rule - broader Supplier/Brand
     * assignment scoping stays CUSTOMER REVIEW). Validates orderable lines
     * up front so an ADMIN never receives an unapprovable Draft in the queue.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder submitForApproval(Long id, String performedBy, boolean performerIsAdmin) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));

        if (!PortalOrder.STATUS_DRAFT.equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), PortalOrder.STATUS_DRAFT);
        }
        if (!performerIsAdmin && !performedBy.equals(order.getCreatedBy())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the Draft's creator or an ADMIN may submit it for approval");
        }

        validator.validateAndGetOrderableLines(order);

        OffsetDateTime now = OffsetDateTime.now();
        String previousStatus = order.getStatus();

        order.setStatus(PortalOrder.STATUS_PENDING_APPROVAL);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);

        PortalOrder saved = portalOrderRepository.save(order);

        auditEventRepository.saveAll(List.of(
                new AuditEvent(saved.getId(), null, AuditEvent.SUBMITTED_FOR_APPROVAL, null, null, null, performedBy, now),
                new AuditEvent(saved.getId(), null, AuditEvent.STATUS_CHANGED, "status", previousStatus, saved.getStatus(), performedBy, now)
        ));

        return saved;
    }

    /**
     * PENDING_APPROVAL -> APPROVED (ADMIN only - enforced at the controller
     * via @PreAuthorize; Phase 7-C1 10章/11章). The prototype PO No. is
     * assigned here, at approval, replacing the pre-7-C1 Confirm - and, as
     * before, an Order that already carries a PO No. (approve -> return ->
     * re-approve cycle) reuses it, never generates a new one.
     *
     * If the approver themselves changed any Draft field after this Order was
     * last submitted (the edit-and-approve path), APPROVED_WITH_CHANGES is
     * recorded instead of ORDER_APPROVED - the individual Before/After rows
     * are already on the trail from the Draft saves.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder approve(Long id, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));

        if (!PortalOrder.STATUS_PENDING_APPROVAL.equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), PortalOrder.STATUS_PENDING_APPROVAL);
        }

        // Never trust that the Draft is still orderable just because it was
        // at submission time (ADMIN edits may have zeroed quantities).
        validator.validateAndGetOrderableLines(order);

        OffsetDateTime now = OffsetDateTime.now();
        String previousStatus = order.getStatus();

        boolean isFirstApproval = order.getPrototypePoNo() == null;
        String prototypePoNo = isFirstApproval ? poNoGenerator.generate(LocalDate.now()) : order.getPrototypePoNo();

        order.setPrototypePoNo(prototypePoNo);
        order.setStatus(PortalOrder.STATUS_APPROVED);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);

        PortalOrder saved = portalOrderRepository.save(order);

        String approvalEventType = approverChangedDraftSinceSubmission(saved.getId(), performedBy)
                ? AuditEvent.APPROVED_WITH_CHANGES
                : AuditEvent.ORDER_APPROVED;

        List<AuditEvent> events = new ArrayList<>();
        events.add(new AuditEvent(saved.getId(), null, approvalEventType,
                isFirstApproval ? "prototypePoNo" : null, null, prototypePoNo, performedBy, now));
        events.add(new AuditEvent(saved.getId(), null, AuditEvent.STATUS_CHANGED, "status", previousStatus, saved.getStatus(), performedBy, now));
        auditEventRepository.saveAll(events);

        return saved;
    }

    /** True when the approver personally wrote Draft-field-change Audit rows
     * after the latest SUBMITTED_FOR_APPROVAL - the marker for the
     * edit-and-approve path (Phase 7-C1 11章). */
    private boolean approverChangedDraftSinceSubmission(Long orderId, String approver) {
        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(orderId);
        OffsetDateTime lastSubmission = null;
        for (AuditEvent e : trail) {
            if (AuditEvent.SUBMITTED_FOR_APPROVAL.equals(e.getEventType())) {
                lastSubmission = e.getPerformedAt();
            }
        }
        if (lastSubmission == null) {
            return false;
        }
        Set<String> draftFieldChangeTypes = Set.of(
                AuditEvent.ORDER_QTY_CHANGED, AuditEvent.ORDER_DATE_CHANGED,
                AuditEvent.REQUESTED_DELIVERY_CHANGED, AuditEvent.REMARK_CHANGED);
        for (AuditEvent e : trail) {
            if (draftFieldChangeTypes.contains(e.getEventType())
                    && approver.equals(e.getPerformedBy())
                    && !e.getPerformedAt().isBefore(lastSubmission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * PENDING_APPROVAL -> DRAFT with a mandatory reason (ADMIN only -
     * enforced at the controller; Phase 7-C1 12章). The reason is stored in
     * the Audit row's note so the Draft screen can surface it to the
     * OPERATOR ("差し戻されました" banner).
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder returnForCorrection(Long id, String reason, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));

        if (!PortalOrder.STATUS_PENDING_APPROVAL.equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), PortalOrder.STATUS_PENDING_APPROVAL);
        }
        if (reason == null || reason.isBlank()) {
            throw new com.glv.gsysportal.exception.ReturnReasonRequiredException();
        }

        OffsetDateTime now = OffsetDateTime.now();
        String previousStatus = order.getStatus();

        order.setStatus(PortalOrder.STATUS_DRAFT);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);

        PortalOrder saved = portalOrderRepository.save(order);

        AuditEvent returned = new AuditEvent(saved.getId(), null, AuditEvent.RETURNED_FOR_CORRECTION,
                null, null, null, performedBy, now);
        returned.setNote(reason.trim());
        auditEventRepository.saveAll(List.of(
                returned,
                new AuditEvent(saved.getId(), null, AuditEvent.STATUS_CHANGED, "status", previousStatus, saved.getStatus(), performedBy, now)
        ));

        return saved;
    }

    /**
     * APPROVED -> DRAFT (ADMIN only - un-approving is an ADMIN act, enforced
     * at the controller). {@code prototype_po_no} is deliberately left
     * untouched - never cleared, never re-generated on a later re-approval of
     * the same Order, so Audit/Tracking can follow one Order under one PO No.
     * across revisions.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder returnToDraft(Long id, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));

        if (!PortalOrder.STATUS_APPROVED.equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), PortalOrder.STATUS_APPROVED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String previousStatus = order.getStatus();

        order.setStatus(PortalOrder.STATUS_DRAFT);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);

        PortalOrder saved = portalOrderRepository.save(order);

        auditEventRepository.saveAll(List.of(
                new AuditEvent(saved.getId(), null, AuditEvent.STATUS_CHANGED, "status", previousStatus, saved.getStatus(), performedBy, now),
                new AuditEvent(saved.getId(), null, AuditEvent.ORDER_RETURNED_TO_DRAFT, null, null, null, performedBy, now)
        ));

        return saved;
    }

    /**
     * APPROVED -> SENT -> AWAITING_SUPPLIER, all in one transaction
     * (implementation instructions 3章/4章). SENT is persisted as a genuine
     * intermediate Status for Audit/History accuracy, but the user is always
     * shown the resting state AWAITING_SUPPLIER - no caller ever observes an
     * Order sitting in SENT. No real email is ever sent; nothing in this
     * method touches any mail transport (implementation instructions 3章).
     *
     * Also initializes the Supplier Response aggregate ({@link SupplierResponse}
     * + one {@link SupplierResponseDetail} per non-removed line) here, at the
     * one point where "what the Supplier was actually told" needs to be
     * Snapshotted (orderedQty/requestedDelivery), since Save Supplier
     * Response (implementation instructions 10章) only ever updates the
     * Supplier's answer, never these Original values.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder demoSend(Long id, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));

        if (!PortalOrder.STATUS_APPROVED.equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), PortalOrder.STATUS_APPROVED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<AuditEvent> events = new ArrayList<>();

        events.add(new AuditEvent(order.getId(), null, AuditEvent.STATUS_CHANGED, "status",
                PortalOrder.STATUS_APPROVED, PortalOrder.STATUS_SENT, performedBy, now));
        events.add(new AuditEvent(order.getId(), null, AuditEvent.DEMO_SENT, null, null, null, performedBy, now));
        events.add(new AuditEvent(order.getId(), null, AuditEvent.STATUS_CHANGED, "status",
                PortalOrder.STATUS_SENT, PortalOrder.STATUS_AWAITING_SUPPLIER, performedBy, now));

        order.setStatus(PortalOrder.STATUS_AWAITING_SUPPLIER);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);

        initializeSupplierResponse(order, now);

        PortalOrder saved = portalOrderRepository.save(order);
        auditEventRepository.saveAll(events);

        return saved;
    }

    private void initializeSupplierResponse(PortalOrder order, OffsetDateTime now) {
        SupplierResponse response = new SupplierResponse();
        response.setPortalOrderId(order.getId());
        response.setResponseStatus(SupplierResponse.STATUS_PARTIAL);
        response.setCreatedAt(now);
        response.setUpdatedAt(now);

        for (PortalOrderDetail line : order.getDetails()) {
            if (line.isRemoved()) {
                continue;
            }
            SupplierResponseDetail detail = new SupplierResponseDetail();
            detail.setSupplierResponse(response);
            detail.setPortalOrderDetail(line);
            // Original values the Supplier was actually told - snapshotted
            // here, once, and never changed afterwards.
            detail.setOrderedQty(line.getOrderQty());
            detail.setRequestedDelivery(order.getRequestedDelivery());
            detail.setCreatedAt(now);
            detail.setUpdatedAt(now);
            response.getDetails().add(detail);
        }

        supplierResponseRepository.save(response);
    }
}
