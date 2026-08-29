package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderRevision;
import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.domain.SupplierResponse;
import com.glv.gsysportal.domain.SupplierResponseDetail;
import com.glv.gsysportal.dto.request.AgreeResponseRequest;
import com.glv.gsysportal.dto.request.ReopenAgreementRequest;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.response.AttentionSummary;
import com.glv.gsysportal.dto.response.ResponseDifferenceView;
import com.glv.gsysportal.dto.response.SupplierResponseDetailView;
import com.glv.gsysportal.dto.response.SupplierResponseHistoryEntry;
import com.glv.gsysportal.dto.response.SupplierResponseSummary;
import com.glv.gsysportal.dto.response.SupplierResponseView;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.InvalidConfirmedQtyException;
import com.glv.gsysportal.exception.InvalidOrderStatusException;
import com.glv.gsysportal.exception.InvalidStatusTransitionException;
import com.glv.gsysportal.exception.ReopenReasonRequiredException;
import com.glv.gsysportal.exception.OrderNotAgreedException;
import com.glv.gsysportal.exception.ResponseNotAgreeableException;
import com.glv.gsysportal.exception.SupplierResponseIncompleteException;
import com.glv.gsysportal.exception.UnacknowledgedAttentionException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRevisionRepository;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import com.glv.gsysportal.repository.prototype.SupplierResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * GET/PUT /api/orders/{id}/supplier-response and
 * POST /api/orders/{id}/supplier-response/confirm (implementation
 * instructions 9章/10章/20章/21章). The single most important rule
 * throughout this class: {@code confirmedQty} is an {@link Integer}, and
 * {@code null} ("not yet answered") is never conflated with {@code 0} (an
 * explicit zero answer) - implementation instructions 11章.
 *
 * <p>Phase 7-C5: extended to be Revision-aware (5章) - the "current" Response
 * is now the one linked to the Order's currently-sent
 * {@link PortalOrderRevision}, not simply "the Order's one Response" (which
 * no longer holds once an Order has been corrected and re-sent). Also adds
 * Agreement (11章) and Reopen (18章), both explicit Business Actions,
 * neither of which is ever implied by Confirm (10章 "Supplier Response確定
 * ≠ AGREED").
 */
@Service
public class SupplierResponseService {

    private final PortalOrderRepository portalOrderRepository;
    private final SupplierResponseRepository supplierResponseRepository;
    private final PortalOrderRevisionRepository revisionRepository;
    private final OrderAttentionRepository orderAttentionRepository;
    private final AuditEventRepository auditEventRepository;
    private final PortalUserRepository portalUserRepository;

    public SupplierResponseService(PortalOrderRepository portalOrderRepository,
                                    SupplierResponseRepository supplierResponseRepository,
                                    PortalOrderRevisionRepository revisionRepository,
                                    OrderAttentionRepository orderAttentionRepository,
                                    AuditEventRepository auditEventRepository,
                                    PortalUserRepository portalUserRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.supplierResponseRepository = supplierResponseRepository;
        this.revisionRepository = revisionRepository;
        this.orderAttentionRepository = orderAttentionRepository;
        this.auditEventRepository = auditEventRepository;
        this.portalUserRepository = portalUserRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public SupplierResponseView getSupplierResponse(Long orderId) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        validateStatusReadable(order.getStatus());
        PortalOrderRevision revision = requireCurrentRevision(order);
        SupplierResponse response = requireResponse(orderId, revision.getId());
        return buildView(order, revision, response, true);
    }

    /** Phase 7-C5 21章: past, READ ONLY Response for a specific Revision - any
     * authenticated user, same visibility as the current Response GET. The
     * Order's own current status is irrelevant here (history is always
     * readable regardless of where the Order has moved on to since). */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public SupplierResponseView getSupplierResponseHistory(Long orderId, int revisionNo) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        PortalOrderRevision revision = revisionRepository.findByPortalOrderIdAndRevisionNo(orderId, revisionNo)
                .orElseThrow(() -> new IllegalStateException("Revision " + revisionNo + " not found for Order " + orderId));
        SupplierResponse response = requireResponse(orderId, revision.getId());
        boolean isCurrent = Objects.equals(order.getCurrentRevisionNo(), revisionNo);
        return buildView(order, revision, response, isCurrent);
    }

    /** Phase 7-C5 20章/21章: browsable Response History list (Response1,
     * Response2, ...). */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<SupplierResponseHistoryEntry> getResponseHistory(Long orderId) {
        if (!portalOrderRepository.existsById(orderId)) {
            throw new DraftNotFoundException(orderId);
        }
        List<PortalOrderRevision> revisions = revisionRepository.findByPortalOrderIdOrderByRevisionNoAsc(orderId);
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        List<SupplierResponseHistoryEntry> entries = new ArrayList<>();
        for (PortalOrderRevision revision : revisions) {
            supplierResponseRepository.findByPortalOrderIdAndOrderRevisionId(orderId, revision.getId())
                    .ifPresent(r -> entries.add(new SupplierResponseHistoryEntry(
                            r.getId(), revision.getRevisionNo(), r.getResponseDate(), r.getResponseStatus(),
                            Objects.equals(order.getCurrentRevisionNo(), revision.getRevisionNo()),
                            r.getAgreedBy(), displayNameOf(r.getAgreedBy()), r.getAgreedAt(),
                            r.getReopenedBy(), displayNameOf(r.getReopenedBy()), r.getReopenedAt(), r.getReopenReason()
                    )));
        }
        return entries;
    }

    /** Phase 7-H: same "resolve, tolerate a since-removed account" idiom as
     * OrderHistoryService's performedByDisplayName lookup - null username
     * (agreedBy/reopenedBy are null until that Business Action ever
     * happened) resolves to null without a lookup. */
    private String displayNameOf(String username) {
        if (username == null) {
            return null;
        }
        return portalUserRepository.findByUsername(username).map(PortalUser::getDisplayName).orElse(null);
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
        PortalOrderRevision revision = requireCurrentRevision(order);
        SupplierResponse response = requireResponse(orderId, revision.getId());

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

        return buildView(order, revision, response, true);
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
        PortalOrderRevision revision = requireCurrentRevision(order);
        SupplierResponse response = requireResponse(orderId, revision.getId());

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

    /**
     * Phase 7-C5 11章: explicit Business Action, ADMIN only (enforced at the
     * Controller). Preconditions: Order is SUPPLIER_CONFIRMED, {@code responseId}
     * is the CURRENT Response (never a past, superseded one - 409
     * RESPONSE_NOT_AGREEABLE otherwise), and every ACTIVE Attention on the
     * Order has been acknowledged - unless {@code forceAgree} is set (409
     * UNACKNOWLEDGED_ATTENTION otherwise). Never auto-inferred from Confirm.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder agree(Long orderId, Long responseId, AgreeResponseRequest request, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (!PortalOrder.STATUS_SUPPLIER_CONFIRMED.equals(order.getStatus())) {
            throw new ResponseNotAgreeableException(orderId, responseId);
        }
        PortalOrderRevision revision = requireCurrentRevision(order);
        SupplierResponse response = requireResponse(orderId, revision.getId());
        if (!response.getId().equals(responseId)) {
            throw new ResponseNotAgreeableException(orderId, responseId);
        }

        boolean hasUnacknowledged = !orderAttentionRepository.findByPortalOrderIdAndActiveTrue(orderId).isEmpty();
        if (hasUnacknowledged && !request.forceAgree()) {
            throw new UnacknowledgedAttentionException(orderId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String previousStatus = order.getStatus();

        response.setAgreedBy(performedBy);
        response.setAgreedAt(now);
        supplierResponseRepository.save(response);

        order.setStatus(PortalOrder.STATUS_AGREED);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);
        PortalOrder saved = portalOrderRepository.save(order);

        auditEventRepository.saveAll(List.of(
                new AuditEvent(orderId, null, AuditEvent.SUPPLIER_RESPONSE_AGREED, null, null, null, performedBy, now),
                new AuditEvent(orderId, null, AuditEvent.STATUS_CHANGED, "status", previousStatus, saved.getStatus(), performedBy, now)
        ));

        return saved;
    }

    /**
     * Phase 7-C5 18章: AGREED -> SUPPLIER_CONFIRMED with a mandatory reason,
     * ADMIN only (enforced at the Controller). {@code agreed_by}/
     * {@code agreed_at} are deliberately never cleared ("履歴を消さない" -
     * direct data overwrite is forbidden) - {@code reopened_by}/{@code
     * reopened_at}/{@code reopen_reason} record the most recent Reopen
     * alongside them instead.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder reopenAgreement(Long orderId, Long responseId, ReopenAgreementRequest request, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (!PortalOrder.STATUS_AGREED.equals(order.getStatus())) {
            throw new OrderNotAgreedException(orderId, order.getStatus());
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ReopenReasonRequiredException();
        }
        PortalOrderRevision revision = requireCurrentRevision(order);
        SupplierResponse response = requireResponse(orderId, revision.getId());
        if (!response.getId().equals(responseId)) {
            throw new ResponseNotAgreeableException(orderId, responseId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        String previousStatus = order.getStatus();

        response.setReopenedBy(performedBy);
        response.setReopenedAt(now);
        response.setReopenReason(request.reason().trim());
        supplierResponseRepository.save(response);

        order.setStatus(PortalOrder.STATUS_SUPPLIER_CONFIRMED);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);
        PortalOrder saved = portalOrderRepository.save(order);

        AuditEvent reopened = new AuditEvent(orderId, null, AuditEvent.AGREEMENT_REOPENED, null, null, null, performedBy, now);
        reopened.setNote(request.reason().trim());
        auditEventRepository.saveAll(List.of(
                reopened,
                new AuditEvent(orderId, null, AuditEvent.STATUS_CHANGED, "status", previousStatus, saved.getStatus(), performedBy, now)
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
        // Phase 7-C5 7章: explicitly selected only, NEVER derived from
        // confirmedQty - a null supplyStatus in the request means "leave
        // unchanged" (same "included line, field-level intent" convention as
        // responseNote above), not "clear it".
        if (lineUpdate.supplyStatus() != null) {
            detail.setSupplyStatus(lineUpdate.supplyStatus());
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
        // Phase 7-C5 8章/9章: [PROTOTYPE DECISION] any explicitly-selected
        // Supply Status other than AVAILABLE warrants review - the official
        // per-value business definition remains [TBD - CUSTOMER REVIEW] (24章).
        if (detail.getSupplyStatus() != null && !"AVAILABLE".equals(detail.getSupplyStatus())) {
            ensureLineAttentionActive(orderId, portalOrderDetailId, OrderAttention.SUPPLY_STATUS_CHANGED, now);
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

    private PortalOrderRevision requireCurrentRevision(PortalOrder order) {
        Integer revisionNo = order.getCurrentRevisionNo();
        if (revisionNo == null) {
            throw new IllegalStateException("Order " + order.getId() + " has no current Revision - Demo Send should have created one");
        }
        return revisionRepository.findByPortalOrderIdAndRevisionNo(order.getId(), revisionNo)
                .orElseThrow(() -> new IllegalStateException("Revision " + revisionNo + " not found for Order " + order.getId()));
    }

    private SupplierResponse requireResponse(Long orderId, Long orderRevisionId) {
        return supplierResponseRepository.findByPortalOrderIdAndOrderRevisionId(orderId, orderRevisionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Supplier Response missing for Order " + orderId + " Revision " + orderRevisionId
                                + " - Demo Send should have initialized it"));
    }

    private static void validateStatusReadable(String status) {
        if (!PortalOrder.STATUS_AWAITING_SUPPLIER.equals(status) && !PortalOrder.STATUS_SUPPLIER_CONFIRMED.equals(status)
                && !PortalOrder.STATUS_AGREED.equals(status)) {
            throw new InvalidOrderStatusException(status);
        }
    }

    private SupplierResponseView buildView(PortalOrder order, PortalOrderRevision revision, SupplierResponse response, boolean isCurrent) {
        List<OrderAttention> activeAttentions = orderAttentionRepository.findByPortalOrderIdAndActiveTrue(order.getId());

        List<SupplierResponseDetailView> detailViews = response.getDetails().stream()
                .map(d -> toDetailView(d, activeAttentions))
                .toList();

        List<AttentionSummary> orderAttentions = activeAttentions.stream()
                .filter(a -> a.getPortalOrderDetailId() == null)
                .map(a -> new AttentionSummary(a.getId(), a.getAttentionType()))
                .toList();

        int totalOrderedQty = response.getDetails().stream().mapToInt(SupplierResponseDetail::getOrderedQty).sum();
        SupplierResponseSummary summary = computeSummary(response.getDetails());
        List<ResponseDifferenceView> differences = OrderRevisionService.computeDifferences(revision, response);

        return new SupplierResponseView(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getSupplierCode(), order.getSupplierNameSnapshot(),
                order.getBrandCode(), order.getBrandNameSnapshot(),
                order.getOrderDate(), order.getStatus(),
                totalOrderedQty, order.getTotalAmount(),
                response.getResponseDate(), response.getResponseNote(), response.getResponseStatus(),
                detailViews, orderAttentions, summary,
                response.getId(), revision.getRevisionNo(), isCurrent, differences,
                response.getAgreedBy(), displayNameOf(response.getAgreedBy()), response.getAgreedAt(),
                response.getReopenedBy(), displayNameOf(response.getReopenedBy()), response.getReopenedAt(), response.getReopenReason()
        );
    }

    private static SupplierResponseDetailView toDetailView(SupplierResponseDetail d, List<OrderAttention> activeAttentions) {
        var pod = d.getPortalOrderDetail();
        List<AttentionSummary> attentions = activeAttentions.stream()
                .filter(a -> pod.getId().equals(a.getPortalOrderDetailId()))
                .map(a -> new AttentionSummary(a.getId(), a.getAttentionType()))
                .toList();
        List<String> warnings = new ArrayList<>();
        if (d.getConfirmedQty() != null && d.getConfirmedQty() > d.getOrderedQty()) {
            warnings.add("CONFIRMED_QTY_EXCEEDS_ORDERED_QTY");
        }
        return new SupplierResponseDetailView(
                d.getId(), pod.getSku(), pod.getItemNameSnapshot(), d.getOrderedQty(), d.getConfirmedQty(),
                d.getRequestedDelivery(), d.getConfirmedDelivery(), d.getResponseNote(), d.getSupplyStatus(), d.isConfirmed(),
                attentions, warnings
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
