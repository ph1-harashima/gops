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

/**
 * Confirm Order (implementation instructions 7章/8章/9章) and Return to
 * Draft (implementation instructions 13章/14章/15章) - the two Status
 * transition operations added in Step 3. Both run entirely inside one
 * Prototype WRITE transaction each: fetch -> guard the transition ->
 * re-validate -> mutate -> save -> write Audit, so a Status change with no
 * matching Audit row can never occur (implementation instructions 8章).
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
     * DRAFT -> READY_TO_ORDER. Requires the current Status to be exactly
     * DRAFT - a duplicate/re-sent Confirm on an already-READY_TO_ORDER order
     * is rejected outright (409) before anything else runs, which is what
     * guarantees idempotency: no new PO No. is ever generated and no
     * duplicate Audit rows are ever written for a repeat Confirm
     * (implementation instructions 9章).
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder confirm(Long id, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));

        if (!PortalOrder.STATUS_DRAFT.equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), PortalOrder.STATUS_DRAFT);
        }

        // Re-validate even though the Frontend normally only reaches Confirm
        // via a successful Preview - Frontend state is never trusted
        // (implementation instructions 7章).
        validator.validateAndGetOrderableLines(order);

        OffsetDateTime now = OffsetDateTime.now();
        String previousStatus = order.getStatus();

        // Implementation instructions 14章: a prior Confirm -> Return to Draft
        // -> re-Confirm cycle on the SAME Order must reuse the existing
        // prototype_po_no, never generate a new one. Only assign a fresh PO
        // No. the first time this Order is ever confirmed.
        boolean isFirstConfirm = order.getPrototypePoNo() == null;
        String prototypePoNo = isFirstConfirm ? poNoGenerator.generate(LocalDate.now()) : order.getPrototypePoNo();

        order.setPrototypePoNo(prototypePoNo);
        order.setStatus(PortalOrder.STATUS_READY_TO_ORDER);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);

        PortalOrder saved = portalOrderRepository.save(order);

        List<AuditEvent> events = new ArrayList<>();
        if (isFirstConfirm) {
            events.add(new AuditEvent(saved.getId(), null, AuditEvent.ORDER_READY, "prototypePoNo", null, prototypePoNo, performedBy, now));
        } else {
            events.add(new AuditEvent(saved.getId(), null, AuditEvent.ORDER_READY, null, null, prototypePoNo, performedBy, now));
        }
        events.add(new AuditEvent(saved.getId(), null, AuditEvent.STATUS_CHANGED, "status", previousStatus, saved.getStatus(), performedBy, now));
        auditEventRepository.saveAll(events);

        return saved;
    }

    /**
     * READY_TO_ORDER -> DRAFT only. {@code prototype_po_no} is deliberately
     * left untouched - never cleared, never re-generated on a later Confirm
     * of the same Order (implementation instructions 14章), so Audit/Tracking
     * can follow one Order under one PO No. across revisions.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder returnToDraft(Long id, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));

        if (!PortalOrder.STATUS_READY_TO_ORDER.equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), PortalOrder.STATUS_READY_TO_ORDER);
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
     * READY_TO_ORDER -> SENT -> AWAITING_SUPPLIER, all in one transaction
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

        if (!PortalOrder.STATUS_READY_TO_ORDER.equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), PortalOrder.STATUS_READY_TO_ORDER);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<AuditEvent> events = new ArrayList<>();

        events.add(new AuditEvent(order.getId(), null, AuditEvent.STATUS_CHANGED, "status",
                PortalOrder.STATUS_READY_TO_ORDER, PortalOrder.STATUS_SENT, performedBy, now));
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
