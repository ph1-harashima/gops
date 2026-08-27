package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.SupplierResponse;
import com.glv.gsysportal.domain.SupplierResponseDetail;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.response.SupplierResponseDetailView;
import com.glv.gsysportal.dto.response.SupplierResponseSummary;
import com.glv.gsysportal.dto.response.SupplierResponseView;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.InvalidConfirmedQtyException;
import com.glv.gsysportal.exception.InvalidOrderStatusException;
import com.glv.gsysportal.exception.InvalidStatusTransitionException;
import com.glv.gsysportal.exception.SupplierResponseIncompleteException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.SupplierResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * GET/PUT /api/orders/{id}/supplier-response and
 * POST /api/orders/{id}/supplier-response/confirm (implementation
 * instructions 9章/10章/20章/21章). The single most important rule
 * throughout this class: {@code confirmedQty} is an {@link Integer}, and
 * {@code null} ("not yet answered") is never conflated with {@code 0} (an
 * explicit zero answer) - implementation instructions 11章.
 */
@Service
public class SupplierResponseService {

    private final PortalOrderRepository portalOrderRepository;
    private final SupplierResponseRepository supplierResponseRepository;
    private final OrderAttentionRepository orderAttentionRepository;
    private final AuditEventRepository auditEventRepository;

    public SupplierResponseService(PortalOrderRepository portalOrderRepository,
                                    SupplierResponseRepository supplierResponseRepository,
                                    OrderAttentionRepository orderAttentionRepository,
                                    AuditEventRepository auditEventRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.supplierResponseRepository = supplierResponseRepository;
        this.orderAttentionRepository = orderAttentionRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public SupplierResponseView getSupplierResponse(Long orderId) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        validateStatusReadable(order.getStatus());
        SupplierResponse response = requireResponse(orderId);
        return buildView(order, response);
    }

    /**
     * Only permitted while AWAITING_SUPPLIER (implementation instructions
     * 10章) - once Confirmed, no further edits (SUPPLIER_CONFIRMED rejects
     * with 409, same status-guard pattern as Confirm Order / Return to
     * Draft). Lines not present in {@code request.details()} are left
     * untouched (partial save).
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public SupplierResponseView saveSupplierResponse(Long orderId, SaveSupplierResponseRequest request, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (!PortalOrder.STATUS_AWAITING_SUPPLIER.equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), PortalOrder.STATUS_AWAITING_SUPPLIER);
        }
        SupplierResponse response = requireResponse(orderId);

        OffsetDateTime now = OffsetDateTime.now();
        List<AuditEvent> events = new ArrayList<>();

        if (request.responseDate() != null) {
            response.setResponseDate(request.responseDate());
        }
        if (request.responseNote() != null) {
            response.setResponseNote(request.responseNote());
        }
        response.setReceivedBy(performedBy);
        response.setUpdatedAt(now);

        if (request.details() != null) {
            for (SaveSupplierResponseRequest.LineUpdate lineUpdate : request.details()) {
                applyLineUpdate(orderId, response, lineUpdate, now, performedBy, events);
            }
        }

        syncPartialConfirmationAttention(order, response, now, performedBy, events);

        supplierResponseRepository.save(response);
        if (!events.isEmpty()) {
            auditEventRepository.saveAll(events);
        }

        return buildView(order, response);
    }

    /**
     * [PROTOTYPE DECISION] (implementation instructions 20章) the provisional
     * completion condition is "every non-removed line has a non-null
     * confirmedQty" - Confirmed Delivery is not required. The formal
     * completion condition is [TBD - CUSTOMER REVIEW] (Requirements MD 27.4).
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder confirmSupplierResponse(Long orderId, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (!PortalOrder.STATUS_AWAITING_SUPPLIER.equals(order.getStatus())) {
            throw new InvalidStatusTransitionException(order.getStatus(), PortalOrder.STATUS_AWAITING_SUPPLIER);
        }
        SupplierResponse response = requireResponse(orderId);

        boolean allAnswered = response.getDetails().stream().allMatch(d -> d.getConfirmedQty() != null);
        if (!allAnswered) {
            throw new SupplierResponseIncompleteException(orderId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String previousStatus = order.getStatus();

        response.setResponseStatus(SupplierResponse.STATUS_CONFIRMED);
        response.setUpdatedAt(now);
        supplierResponseRepository.save(response);

        order.setStatus(PortalOrder.STATUS_SUPPLIER_CONFIRMED);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);
        PortalOrder saved = portalOrderRepository.save(order);

        auditEventRepository.saveAll(List.of(
                new AuditEvent(saved.getId(), null, AuditEvent.SUPPLIER_RESPONSE_RECEIVED, null, null, null, performedBy, now),
                new AuditEvent(saved.getId(), null, AuditEvent.STATUS_CHANGED, "status", previousStatus, saved.getStatus(), performedBy, now)
        ));

        return saved;
    }

    private void applyLineUpdate(Long orderId, SupplierResponse response, SaveSupplierResponseRequest.LineUpdate lineUpdate,
                                  OffsetDateTime now, String performedBy, List<AuditEvent> events) {
        SupplierResponseDetail detail = response.getDetails().stream()
                .filter(d -> d.getId().equals(lineUpdate.detailId()))
                .findFirst()
                .orElse(null);
        if (detail == null) {
            return; // unknown/foreign detail id - silently ignored, same convention as Save Draft
        }
        if (lineUpdate.confirmedQty() != null && lineUpdate.confirmedQty() < 0) {
            throw new InvalidConfirmedQtyException(lineUpdate.confirmedQty());
        }

        Long portalOrderDetailId = detail.getPortalOrderDetail().getId();
        Integer oldQty = detail.getConfirmedQty();
        var oldDelivery = detail.getConfirmedDelivery();

        // implementation instructions 16章: only write Audit when the value
        // actually changed since the last Save - Objects.equals(null, null)
        // correctly treats "still unanswered" as no-change.
        if (!Objects.equals(oldQty, lineUpdate.confirmedQty())) {
            events.add(new AuditEvent(orderId, portalOrderDetailId, AuditEvent.QUANTITY_CHANGED, "confirmedQty",
                    oldQty == null ? null : String.valueOf(oldQty),
                    lineUpdate.confirmedQty() == null ? null : String.valueOf(lineUpdate.confirmedQty()),
                    performedBy, now));
            detail.setConfirmedQty(lineUpdate.confirmedQty());
        }
        if (!Objects.equals(oldDelivery, lineUpdate.confirmedDelivery())) {
            events.add(new AuditEvent(orderId, portalOrderDetailId, AuditEvent.DELIVERY_CHANGED, "confirmedDelivery",
                    oldDelivery == null ? null : oldDelivery.toString(),
                    lineUpdate.confirmedDelivery() == null ? null : lineUpdate.confirmedDelivery().toString(),
                    performedBy, now));
            detail.setConfirmedDelivery(lineUpdate.confirmedDelivery());
        }
        if (lineUpdate.responseNote() != null) {
            detail.setResponseNote(lineUpdate.responseNote());
        }
        detail.setConfirmed(detail.getConfirmedQty() != null);
        detail.setUpdatedAt(now);

        // Attention (implementation instructions 12章/13章/15章): created once
        // per (order, detail, type) and left ACTIVE even if a later edit
        // reconciles the value (implementation instructions 23章) - no Audit
        // here, the QUANTITY_CHANGED/DELIVERY_CHANGED row above already
        // records the underlying value change.
        if (detail.getConfirmedQty() != null && !detail.getConfirmedQty().equals(detail.getOrderedQty())) {
            ensureLineAttentionActive(orderId, portalOrderDetailId, OrderAttention.QUANTITY_CHANGED, now);
        }
        if (detail.getConfirmedDelivery() != null && !detail.getConfirmedDelivery().equals(detail.getRequestedDelivery())) {
            ensureLineAttentionActive(orderId, portalOrderDetailId, OrderAttention.DELIVERY_CHANGED, now);
        }
    }

    private void ensureLineAttentionActive(Long orderId, Long portalOrderDetailId, String type, OffsetDateTime now) {
        boolean exists = orderAttentionRepository
                .findByPortalOrderIdAndPortalOrderDetailIdAndAttentionTypeAndActiveTrue(orderId, portalOrderDetailId, type)
                .isPresent();
        if (exists) {
            return;
        }
        OrderAttention attention = new OrderAttention();
        attention.setPortalOrderId(orderId);
        attention.setPortalOrderDetailId(portalOrderDetailId);
        attention.setAttentionType(type);
        attention.setActive(true);
        attention.setDetectedAt(now);
        orderAttentionRepository.save(attention);
    }

    /**
     * PARTIAL_CONFIRMATION is Order-level (implementation instructions 14章)
     * and, unlike QUANTITY_CHANGED/DELIVERY_CHANGED, is treated as a
     * System-generated transient Attention that auto-resolves once every
     * line has an answer (implementation instructions 23章) - the only
     * Attention type this Step auto-resolves without a user Acknowledge.
     */
    private void syncPartialConfirmationAttention(PortalOrder order, SupplierResponse response,
                                                   OffsetDateTime now, String performedBy, List<AuditEvent> events) {
        boolean allAnswered = response.getDetails().stream().allMatch(d -> d.getConfirmedQty() != null);
        Optional<OrderAttention> existing = orderAttentionRepository
                .findByPortalOrderIdAndPortalOrderDetailIdIsNullAndAttentionTypeAndActiveTrue(order.getId(), OrderAttention.PARTIAL_CONFIRMATION);

        if (!allAnswered) {
            if (existing.isEmpty()) {
                OrderAttention attention = new OrderAttention();
                attention.setPortalOrderId(order.getId());
                attention.setAttentionType(OrderAttention.PARTIAL_CONFIRMATION);
                attention.setActive(true);
                attention.setDetectedAt(now);
                orderAttentionRepository.save(attention);
                events.add(new AuditEvent(order.getId(), null, AuditEvent.ATTENTION_ADDED, "attentionType",
                        null, OrderAttention.PARTIAL_CONFIRMATION, performedBy, now));
            }
        } else if (existing.isPresent()) {
            OrderAttention attention = existing.get();
            attention.setActive(false);
            attention.setResolvedAt(now);
            orderAttentionRepository.save(attention);
            events.add(new AuditEvent(order.getId(), null, AuditEvent.ATTENTION_RESOLVED, "attentionType",
                    OrderAttention.PARTIAL_CONFIRMATION, null, performedBy, now));
        }
    }

    private SupplierResponse requireResponse(Long orderId) {
        return supplierResponseRepository.findByPortalOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Supplier Response missing for Order " + orderId + " - Demo Send should have initialized it"));
    }

    private static void validateStatusReadable(String status) {
        if (!PortalOrder.STATUS_AWAITING_SUPPLIER.equals(status) && !PortalOrder.STATUS_SUPPLIER_CONFIRMED.equals(status)) {
            throw new InvalidOrderStatusException(status);
        }
    }

    private SupplierResponseView buildView(PortalOrder order, SupplierResponse response) {
        List<OrderAttention> activeAttentions = orderAttentionRepository.findByPortalOrderIdAndActiveTrue(order.getId());

        List<SupplierResponseDetailView> detailViews = response.getDetails().stream()
                .map(d -> toDetailView(d, activeAttentions))
                .toList();

        List<String> orderAttentionTypes = activeAttentions.stream()
                .filter(a -> a.getPortalOrderDetailId() == null)
                .map(OrderAttention::getAttentionType)
                .toList();

        int totalOrderedQty = response.getDetails().stream().mapToInt(SupplierResponseDetail::getOrderedQty).sum();
        SupplierResponseSummary summary = computeSummary(response.getDetails());

        return new SupplierResponseView(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getSupplierCode(), order.getSupplierNameSnapshot(),
                order.getBrandCode(), order.getBrandNameSnapshot(),
                order.getOrderDate(), order.getStatus(),
                totalOrderedQty, order.getTotalAmount(),
                response.getResponseDate(), response.getResponseNote(), response.getResponseStatus(),
                detailViews, orderAttentionTypes, summary
        );
    }

    private static SupplierResponseDetailView toDetailView(SupplierResponseDetail d, List<OrderAttention> activeAttentions) {
        var pod = d.getPortalOrderDetail();
        List<String> attentionTypes = activeAttentions.stream()
                .filter(a -> pod.getId().equals(a.getPortalOrderDetailId()))
                .map(OrderAttention::getAttentionType)
                .toList();
        List<String> warnings = new ArrayList<>();
        if (d.getConfirmedQty() != null && d.getConfirmedQty() > d.getOrderedQty()) {
            warnings.add("CONFIRMED_QTY_EXCEEDS_ORDERED_QTY");
        }
        return new SupplierResponseDetailView(
                d.getId(), pod.getSku(), pod.getItemNameSnapshot(), d.getOrderedQty(), d.getConfirmedQty(),
                d.getRequestedDelivery(), d.getConfirmedDelivery(), d.getResponseNote(), d.isConfirmed(),
                attentionTypes, warnings
        );
    }

    private static SupplierResponseSummary computeSummary(List<SupplierResponseDetail> details) {
        int total = details.size();
        int answered = (int) details.stream().filter(d -> d.getConfirmedQty() != null).count();
        int unanswered = total - answered;
        int quantityChanged = (int) details.stream()
                .filter(d -> d.getConfirmedQty() != null && !d.getConfirmedQty().equals(d.getOrderedQty())).count();
        int deliveryChanged = (int) details.stream()
                .filter(d -> d.getConfirmedDelivery() != null && !d.getConfirmedDelivery().equals(d.getRequestedDelivery())).count();
        int zeroQty = (int) details.stream().filter(d -> d.getConfirmedQty() != null && d.getConfirmedQty() == 0).count();
        return new SupplierResponseSummary(total, answered, unanswered, quantityChanged, deliveryChanged, zeroQty);
    }
}
