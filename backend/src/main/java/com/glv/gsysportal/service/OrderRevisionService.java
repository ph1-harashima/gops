package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.domain.PortalOrderRevision;
import com.glv.gsysportal.domain.PortalOrderRevisionDetail;
import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.domain.SupplierResponse;
import com.glv.gsysportal.domain.SupplierResponseDetail;
import com.glv.gsysportal.dto.request.CreateRevisionRequest;
import com.glv.gsysportal.dto.response.OrderRevisionLineView;
import com.glv.gsysportal.dto.response.OrderRevisionSummary;
import com.glv.gsysportal.dto.response.ResponseDifferenceView;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.RevisionCreationNotAllowedException;
import com.glv.gsysportal.exception.RevisionReasonRequiredException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
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

/**
 * Phase 7-C5: Order Revision snapshotting, "修正版を作成" (create a corrected
 * version, 13章), Revision History (19章/20章), and Difference Detection
 * (8章). See docs/supplier-response-revision-workflow.md 2章 for the full
 * "a Revision crystallizes at Send time" design reasoning.
 */
@Service
public class OrderRevisionService {

    private final PortalOrderRepository portalOrderRepository;
    private final PortalOrderRevisionRepository revisionRepository;
    private final SupplierResponseRepository supplierResponseRepository;
    private final AuditEventRepository auditEventRepository;
    private final PortalUserRepository portalUserRepository;

    public OrderRevisionService(PortalOrderRepository portalOrderRepository,
                                 PortalOrderRevisionRepository revisionRepository,
                                 SupplierResponseRepository supplierResponseRepository,
                                 AuditEventRepository auditEventRepository,
                                 PortalUserRepository portalUserRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.revisionRepository = revisionRepository;
        this.supplierResponseRepository = supplierResponseRepository;
        this.auditEventRepository = auditEventRepository;
        this.portalUserRepository = portalUserRepository;
    }

    /**
     * Called once, inside {@code OrderStatusTransitionService.demoSend}'s own
     * transaction, at the exact moment a Send happens - never independently.
     * {@code revisionType}/{@code reason} are derived from whether this Order
     * has ever been sent before ({@link PortalOrder#getCurrentRevisionNo()}),
     * reusing the SAME "walk the Audit trail for the latest marker event"
     * pattern as {@code OrderDraftService.resolveReturnReason}, rather than
     * adding a new column to stash a reason between "修正版を作成" and the
     * next Send.
     */
    PortalOrderRevision snapshotForSend(PortalOrder order, OffsetDateTime now, String performedBy) {
        boolean isFirstSend = order.getCurrentRevisionNo() == null;
        int revisionNo = isFirstSend ? 1 : order.getCurrentRevisionNo() + 1;

        PortalOrderRevision revision = new PortalOrderRevision();
        revision.setPortalOrderId(order.getId());
        revision.setRevisionNo(revisionNo);
        revision.setRevisionType(isFirstSend ? PortalOrderRevision.TYPE_INITIAL : PortalOrderRevision.TYPE_CORRECTION);
        revision.setReason(isFirstSend ? null : latestRevisionCreatedReason(order.getId()));
        revision.setCreatedBy(performedBy);
        revision.setCreatedAt(now);

        for (PortalOrderDetail line : order.getDetails()) {
            if (line.isRemoved()) {
                continue;
            }
            PortalOrderRevisionDetail detail = new PortalOrderRevisionDetail();
            detail.setRevision(revision);
            detail.setSkuCode(line.getSku());
            detail.setItemNameSnapshot(line.getItemNameSnapshot());
            detail.setRecommendedQty(line.getRecommendedQty());
            detail.setOrderedQty(line.getOrderQty());
            detail.setRequestedDelivery(order.getRequestedDelivery());
            detail.setUnitPrice(line.getUnitPrice());
            detail.setCreatedAt(now);
            revision.getDetails().add(detail);
        }

        order.setCurrentRevisionNo(revisionNo);
        return revisionRepository.save(revision);
    }

    private String latestRevisionCreatedReason(Long orderId) {
        String reason = null;
        for (AuditEvent e : auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(orderId)) {
            if (AuditEvent.ORDER_REVISION_CREATED.equals(e.getEventType())) {
                reason = e.getNote();
            }
        }
        return reason;
    }

    /**
     * "修正版を作成" (Phase 7-C5 13章): SUPPLIER_CONFIRMED -> DRAFT. Does NOT
     * itself create a {@link PortalOrderRevision} row - that happens at the
     * next Demo Send (see {@link #snapshotForSend}). Only APPROVED-Order
     * ownership rules matter for who may edit the resulting Draft again
     * (unchanged, existing {@code OrderDraftPersistenceService.update} rules).
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder createCorrection(Long orderId, CreateRevisionRequest request, String performedBy) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));
        if (!PortalOrder.STATUS_SUPPLIER_CONFIRMED.equals(order.getStatus())) {
            throw new RevisionCreationNotAllowedException(orderId, order.getStatus());
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new RevisionReasonRequiredException();
        }

        PortalOrderRevision currentRevision = revisionRepository
                .findByPortalOrderIdAndRevisionNo(orderId, order.getCurrentRevisionNo())
                .orElseThrow(() -> new IllegalStateException(
                        "SUPPLIER_CONFIRMED Order " + orderId + " has no current Revision - Demo Send should have created one"));
        SupplierResponse currentResponse = supplierResponseRepository
                .findByPortalOrderIdAndOrderRevisionId(orderId, currentRevision.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "SUPPLIER_CONFIRMED Order " + orderId + " has no current Supplier Response"));

        OffsetDateTime now = OffsetDateTime.now();
        String previousStatus = order.getStatus();
        List<AuditEvent> events = new ArrayList<>();

        if (request.applyConfirmedValues()) {
            for (SupplierResponseDetail responseDetail : currentResponse.getDetails()) {
                if (responseDetail.getConfirmedQty() == null) {
                    continue; // never apply an unanswered line (7-C5 13章 "must never auto-confirm")
                }
                PortalOrderDetail line = responseDetail.getPortalOrderDetail();
                int oldQty = line.getOrderQty();
                if (oldQty != responseDetail.getConfirmedQty()) {
                    line.setOrderQty(responseDetail.getConfirmedQty());
                    line.setUpdatedAt(now);
                    events.add(new AuditEvent(orderId, line.getId(), AuditEvent.ORDER_QTY_CHANGED, "orderQty",
                            String.valueOf(oldQty), String.valueOf(responseDetail.getConfirmedQty()), performedBy, now));
                }
            }
        }

        order.setStatus(PortalOrder.STATUS_DRAFT);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);
        PortalOrder saved = portalOrderRepository.save(order);

        AuditEvent revisionCreated = new AuditEvent(orderId, null, AuditEvent.ORDER_REVISION_CREATED,
                null, null, null, performedBy, now);
        revisionCreated.setNote(request.reason().trim());
        events.add(revisionCreated);
        events.add(new AuditEvent(orderId, null, AuditEvent.STATUS_CHANGED, "status", previousStatus, saved.getStatus(), performedBy, now));
        auditEventRepository.saveAll(events);

        return saved;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<OrderRevisionSummary> getRevisionHistory(Long orderId) {
        List<PortalOrderRevision> revisions = revisionRepository.findByPortalOrderIdOrderByRevisionNoAsc(orderId);
        // Phase 7-H: same "resolve once per distinct Login ID, tolerate a
        // since-removed account" idiom as OrderHistoryService's
        // performedByDisplayName lookup - not a new pattern, applied here too
        // so Revision History reads a person's name instead of a Login ID.
        Map<String, String> displayNameByUsername = new HashMap<>();
        revisions.stream().map(PortalOrderRevision::getCreatedBy).distinct().forEach(username ->
                displayNameByUsername.put(username, portalUserRepository.findByUsername(username)
                        .map(PortalUser::getDisplayName)
                        .orElse(null)));
        return revisions.stream()
                .map(r -> toSummary(r, displayNameByUsername.get(r.getCreatedBy())))
                .toList();
    }

    private static OrderRevisionSummary toSummary(PortalOrderRevision r, String createdByDisplayName) {
        List<OrderRevisionLineView> lines = r.getDetails().stream()
                .map(d -> new OrderRevisionLineView(d.getSkuCode(), d.getItemNameSnapshot(), d.getRecommendedQty(),
                        d.getOrderedQty(), d.getRequestedDelivery(), d.getUnitPrice()))
                .toList();
        return new OrderRevisionSummary(r.getId(), r.getRevisionNo(), r.getRevisionType(), r.getReason(),
                r.getCreatedBy(), createdByDisplayName, r.getCreatedAt(), lines);
    }

    /**
     * Phase 7-C5 8章: structured comparison, computed fresh on every read -
     * never persisted (the persisted "needs review" signal stays
     * {@link com.glv.gsysportal.domain.OrderAttention}, per 9章's explicit
     * instruction not to conflate the two).
     */
    static List<ResponseDifferenceView> computeDifferences(PortalOrderRevision revision, SupplierResponse response) {
        List<ResponseDifferenceView> differences = new ArrayList<>();
        for (SupplierResponseDetail d : response.getDetails()) {
            String sku = d.getPortalOrderDetail().getSku();
            PortalOrderRevisionDetail revisionLine = revision.getDetails().stream()
                    .filter(rd -> rd.getSkuCode().equals(sku))
                    .findFirst().orElse(null);
            if (revisionLine == null) {
                continue;
            }
            if (d.getConfirmedQty() == null) {
                differences.add(new ResponseDifferenceView(ResponseDifferenceView.TYPE_UNANSWERED, sku,
                        String.valueOf(revisionLine.getOrderedQty()), null, ResponseDifferenceView.SEVERITY_INFO));
                continue;
            }
            if (!d.getConfirmedQty().equals(revisionLine.getOrderedQty())) {
                differences.add(new ResponseDifferenceView(ResponseDifferenceView.TYPE_QUANTITY_CHANGED, sku,
                        String.valueOf(revisionLine.getOrderedQty()), String.valueOf(d.getConfirmedQty()),
                        ResponseDifferenceView.SEVERITY_WARNING));
            }
            if (d.getConfirmedDelivery() != null && !Objects.equals(d.getConfirmedDelivery(), revisionLine.getRequestedDelivery())) {
                differences.add(new ResponseDifferenceView(ResponseDifferenceView.TYPE_DELIVERY_CHANGED, sku,
                        String.valueOf(revisionLine.getRequestedDelivery()), String.valueOf(d.getConfirmedDelivery()),
                        ResponseDifferenceView.SEVERITY_WARNING));
            }
        }
        return differences;
    }
}
