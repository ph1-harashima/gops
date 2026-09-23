package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.UpdateDraftRequest;
import com.glv.gsysportal.dto.response.OrderDraftDetailResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.DraftDeletionNotAllowedException;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.EmptySkuListException;
import com.glv.gsysportal.exception.MixedSupplierException;
import com.glv.gsysportal.exception.SkuNotFoundException;
import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Create Draft orchestrator (implementation instructions 4章/6章):
 *  Step A - Legacy READ ONLY re-fetch (own transaction, via
 *           {@link LegacyStockReadRepository}, never trusting Frontend values)
 *  Step B - Prototype write (own transaction, via
 *           {@link OrderDraftPersistenceService})
 * No distributed transaction spans both.
 */
@Service
public class OrderDraftService {

    private final LegacyStockReadRepository legacyStockReadRepository;
    private final OrderDraftPersistenceService persistenceService;
    private final PortalOrderRepository portalOrderRepository;
    private final AuditEventRepository auditEventRepository;
    private final OfficialPoIntegrationRequestRepository integrationRequestRepository;

    public OrderDraftService(LegacyStockReadRepository legacyStockReadRepository,
                              OrderDraftPersistenceService persistenceService,
                              PortalOrderRepository portalOrderRepository,
                              AuditEventRepository auditEventRepository,
                              OfficialPoIntegrationRequestRepository integrationRequestRepository) {
        this.legacyStockReadRepository = legacyStockReadRepository;
        this.persistenceService = persistenceService;
        this.portalOrderRepository = portalOrderRepository;
        this.auditEventRepository = auditEventRepository;
        this.integrationRequestRepository = integrationRequestRepository;
    }

    public OrderDraftResponse createDraft(CreateDraftRequest request, String performedBy) {
        Set<String> requestedSkus = new LinkedHashSet<>(request.skus());
        if (requestedSkus.isEmpty()) {
            throw new EmptySkuListException();
        }

        // Step A: Legacy READ ONLY, own transaction (LegacyStockReadRepository.findBySkus
        // is itself @Transactional(readOnly=true, transactionManager="legacyTransactionManager"),
        // fully completes and closes before this method proceeds any further).
        List<LegacyStockRow> legacyRows = legacyStockReadRepository.findBySkus(requestedSkus);

        Set<String> foundSkus = legacyRows.stream().map(LegacyStockRow::itemCd).collect(java.util.stream.Collectors.toSet());
        Set<String> missing = new LinkedHashSet<>(requestedSkus);
        missing.removeAll(foundSkus);
        if (!missing.isEmpty()) {
            throw new SkuNotFoundException(missing);
        }

        Set<String> supplierCodes = legacyRows.stream()
                .map(LegacyStockRow::supplierCd)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (supplierCodes.size() > 1) {
            throw new MixedSupplierException(supplierCodes);
        }

        // Step B: Prototype write, own transaction.
        PortalOrder saved = persistenceService.create(request, legacyRows, performedBy);
        return toResponse(saved, null);
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public OrderDraftResponse getDraft(Long id) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));
        // @SQLRestriction (see PortalOrder's own Javadoc) does not apply to
        // a direct by-ID lookup like findById (a documented Hibernate
        // characteristic, confirmed live: a soft-deleted row is still
        // returned by findById even though it correctly disappears from
        // findAll/Specification-based queries elsewhere) - checked
        // explicitly here so a soft-deleted Draft is genuinely NotFound
        // through this entry point too.
        if (order.getDeletedAt() != null) {
            throw new DraftNotFoundException(id);
        }
        return toResponse(order, resolveReturnReason(order));
    }

    public OrderDraftResponse updateDraft(Long id, UpdateDraftRequest request, String performedBy, boolean performerIsAdmin) {
        PortalOrder saved = persistenceService.update(id, request, performedBy, performerIsAdmin);
        return toResponse(saved, resolveReturnReason(saved));
    }

    /**
     * G-OPS Operational Workflow Realignment Phase F §16: soft delete only
     * (see {@link PortalOrder}'s own @SQLRestriction Javadoc for why hard
     * delete was rejected - AuditEvent's real, enforced FK to portal_order
     * plus this codebase's own "audit trail is permanent" convention).
     * Allowed only while status=DRAFT AND no downstream process has
     * started - checked directly (not inferred from status alone), since
     * an ADMIN's "差し戻し" (return to Draft) can leave an Order back in
     * DRAFT status while it already has a real Official PO Integration
     * Request and/or Revision/Send history from a PRIOR approval cycle;
     * status=DRAFT alone does not guarantee "never touched downstream".
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public void deleteDraft(Long id, String performedBy, boolean performerIsAdmin) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));
        if (order.getDeletedAt() != null) {
            throw new DraftNotFoundException(id);
        }

        // Same "creator or ADMIN" ownership rule as OrderDraftPersistenceService
        // .update()'s own DRAFT-status branch - deletion is at least as
        // sensitive as editing, so it gets no looser a permission model.
        if (!performerIsAdmin && !performedBy.equals(order.getCreatedBy())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the Draft's creator or an ADMIN may delete it");
        }

        if (!PortalOrder.STATUS_DRAFT.equals(order.getStatus())) {
            throw new DraftDeletionNotAllowedException(id, "status is " + order.getStatus() + ", not DRAFT");
        }
        if (order.getCurrentRevisionNo() != null) {
            throw new DraftDeletionNotAllowedException(id, "already has Revision/Send history (currentRevisionNo=" + order.getCurrentRevisionNo() + ")");
        }
        if (integrationRequestRepository.findFirstByPortalOrderIdOrderByRevisionNoDesc(id).isPresent()) {
            throw new DraftDeletionNotAllowedException(id, "already has an Official PO Integration Request");
        }

        OffsetDateTime now = OffsetDateTime.now();
        auditEventRepository.save(new AuditEvent(id, null,
                AuditEvent.ORDER_DRAFT_DELETED, null, null, null, performedBy, now));
        order.setDeletedAt(now);
        order.setDeletedBy(performedBy);
        portalOrderRepository.save(order);
    }

    /**
     * Phase 7-C1 12章: the Draft screen surfaces WHY a Draft came back. Only
     * meaningful while status=DRAFT and the latest approval-flow event
     * (SUBMITTED_FOR_APPROVAL vs RETURNED_FOR_CORRECTION) is a return - once
     * the OPERATOR re-submits, the banner disappears.
     */
    private String resolveReturnReason(PortalOrder order) {
        if (!PortalOrder.STATUS_DRAFT.equals(order.getStatus())) {
            return null;
        }
        String reason = null;
        for (AuditEvent e : auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId())) {
            if (AuditEvent.RETURNED_FOR_CORRECTION.equals(e.getEventType())) {
                reason = e.getNote();
            } else if (AuditEvent.SUBMITTED_FOR_APPROVAL.equals(e.getEventType())) {
                reason = null;
            }
        }
        return reason;
    }

    static OrderDraftResponse toResponse(PortalOrder order, String returnReason) {
        List<OrderDraftDetailResponse> details = order.getDetails().stream()
                .filter(d -> !d.isRemoved())
                .map(OrderDraftService::toDetailResponse)
                .toList();

        List<String> headerWarnings = details.stream()
                .flatMap(d -> d.warningCodes().stream())
                .distinct()
                .toList();

        return new OrderDraftResponse(
                order.getId(),
                order.getDraftNo(),
                order.getPrototypePoNo(),
                order.getSupplierCode(),
                order.getSupplierNameSnapshot(),
                order.getBrandCode(),
                order.getBrandNameSnapshot(),
                order.getOrderDate(),
                order.getRequestedDelivery(),
                order.getCurrency(),
                order.getStatus(),
                order.getRemark(),
                order.getTotalQty(),
                order.getTotalAmount(),
                order.getDataSource(),
                order.getCreatedBy(),
                order.getCreatedAt(),
                order.getUpdatedBy(),
                order.getUpdatedAt(),
                details,
                headerWarnings,
                returnReason
        );
    }

    private static OrderDraftDetailResponse toDetailResponse(PortalOrderDetail d) {
        return new OrderDraftDetailResponse(
                d.getId(), d.getSku(), d.getItemNameSnapshot(), d.getRecommendedQty(), d.getOrderQty(),
                d.getUnitPrice(), d.getAmount(), d.getCurrentStockSnapshot(), d.getSafetyStockSnapshot(),
                d.getOpenPoSnapshot(), d.getRecentSalesSnapshot(), d.getLeadTimeSnapshot(), d.getItemStatusSnapshot(),
                d.getDataSource(), warningCodes(d.getRecommendedQty(), d.getOrderQty())
        );
    }

    /**
     * Requirements MD 13章 / Phase 0.5 34章: ±50% deviation from Recommended
     * Qty is a Warning, never a hard Error - the response always includes
     * this so the Frontend can display it without a separate call, and it is
     * recomputed fresh on every read (not stored) so it always reflects the
     * current order_qty/recommended_qty pair.
     */
    static List<String> warningCodes(int recommendedQty, int orderQty) {
        boolean significant;
        if (recommendedQty > 0) {
            double ratio = orderQty / (double) recommendedQty;
            significant = ratio < 0.5 || ratio > 1.5;
        } else {
            significant = orderQty > 0;
        }
        return significant ? List.of("ORDER_QTY_DIFFERS_SIGNIFICANTLY") : List.of();
    }
}
